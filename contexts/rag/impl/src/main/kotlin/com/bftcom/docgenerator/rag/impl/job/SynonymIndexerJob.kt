package com.bftcom.docgenerator.rag.impl.job

import com.bftcom.docgenerator.ai.embedding.EmbeddingClient
import com.bftcom.docgenerator.db.NodeDocRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.db.SynonymDictionaryRepository
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.nodedoc.SynonymIndexerRow
import com.bftcom.docgenerator.domain.nodedoc.SynonymStatus
import com.bftcom.docgenerator.domain.synonym.SynonymDictionary
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class SynonymIndexerJob(
    txManager: PlatformTransactionManager,
    private val nodeDocRepo: NodeDocRepository,
    private val synonymRepo: SynonymDictionaryRepository,
    private val nodeRepo: NodeRepository,
    @param:Qualifier("fastCheckChatClient")
    private val fastCheckClient: ChatClient,
    @param:Qualifier("structuredExtractionChatClient")
    private val extractionClient: ChatClient,
    private val embeddingClient: EmbeddingClient,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(txManager)

    @Value("\${docgen.synonym.indexer.batch-size:10}")
    private var batchSize: Int = 10

    @Value("\${docgen.synonym.indexer.locale:ru}")
    private var defaultLocale: String = "ru"

    private val chineseCharsRegex = Regex("[\\u4e00-\\u9fa5]")

    private val globalStopTerms = setOf(
        "назначение", "поведение", "контракт", "описание", "метод", "объект",
        "экземпляр", "реализация", "функционал", "возвращает", "создание",
        "инициализация", "сервис", "контроллер", "репозиторий", "бизнес-логика"
    )

    @PostConstruct
    fun validateConfiguration() {
        require(batchSize in 1..10000) {
            "docgen.synonym.indexer.batch-size must be between 1 and 10000, but was $batchSize"
        }
        log.info("SynonymIndexerJob initialized with batchSize=$batchSize, locale=$defaultLocale")
    }

    @Scheduled(fixedDelayString = "\${docgen.synonym.indexer.poll-ms:60000}")
    fun poll() {
        // 1. Короткая транзакция на захват батча
        val rows = tx.execute {
            nodeDocRepo.lockNextBatchForSynonymIndexing(batchSize)
        } ?: return

        if (rows.isEmpty()) return

        log.info("SynonymIndexerJob: захвачен батч {} узлов", rows.size)

        for (row in rows) {
            val nodeId = row.getNodeId()
            try {
                // 2. Обработка полностью ВНЕ ТРАНЗАКЦИИ (CPU & Network intensive)
                val status = processSingleNode(row)

                // 3. Короткая транзакция на обновление финального статуса
                updateStatusIsolated(nodeId, status)
            } catch (e: Exception) {
                log.error("Критический сбой на nodeId=$nodeId: ${e.message}", e)
                updateStatusIsolated(nodeId, SynonymStatus.PENDING)
            }
        }
    }

    private fun processSingleNode(row: SynonymIndexerRow): SynonymStatus {
        val nodeId = row.getNodeId()
        val docTech = row.getDocTech() ?: return SynonymStatus.SKIPPED_HEURISTIC

        // Имя узла берем без открытия долгой транзакции
        val nodeName = nodeRepo.findById(nodeId).map { it.name }.orElse("") ?: ""

        // Этап 1: Быстрые эвристики
        if (shouldSkipByHeuristic(nodeName)) return SynonymStatus.SKIPPED_HEURISTIC

        val purposeText = extractPurposeSection(docTech)
        if (purposeText == null || purposeText.length < 60) return SynonymStatus.SKIPPED_HEURISTIC

        // Этап 2: LLM Judge
        if (!isBusinessLogicFastCheck(extractContextForJudge(docTech))) return SynonymStatus.SKIPPED_JUDGE

        // Этап 3: LLM Extraction
        val rawPairs = extractSynonymPairs(nodeName, extractContextSections(docTech))
            .filter { isValidPair(it, nodeName) }
            .distinctBy { it.term.lowercase().trim() }

        if (rawPairs.isEmpty()) return SynonymStatus.FAILED_LLM

        // Этап 4: Embedding (Network IO)
        // Сначала собираем данные, потом сохраняем в БД в конце
        val enrichedPairs = rawPairs.mapNotNull { pair ->
            try {
                val tEmb = embeddingClient.embed(pair.term).joinToString(",", "[", "]")
                val dEmb = embeddingClient.embed(pair.description).joinToString(",", "[", "]")
                EnrichedSynonym(pair.term, pair.description, tEmb, dEmb)
            } catch (e: Exception) {
                log.warn("Не удалось создать эмбеддинг для синонима '{}': {}", pair.term, e.message)
                null
            }
        }

        if (enrichedPairs.isEmpty()) return SynonymStatus.FAILED_LLM

        // Этап 5: Сохранение в БД (Короткая транзакция)
        return persistSynonyms(nodeId, enrichedPairs)
    }

    private fun persistSynonyms(nodeId: Long, items: List<EnrichedSynonym>): SynonymStatus {
        return tx.execute {
            // Используем getReferenceById, чтобы не делать лишний SELECT Node, нам нужен только FK
            val nodeRef = nodeRepo.getReferenceById(nodeId)

            items.forEach { item ->
                if (!synonymRepo.existsByTermIgnoreCase(item.term)) {
                    val entity = synonymRepo.save(
                        SynonymDictionary(
                            term = item.term,
                            description = item.description,
                            sourceNode = nodeRef,
                            modelName = embeddingClient.modelName
                        )
                    )
                    val entityId = requireNotNull(entity.id) { "SynonymDictionary must have ID after save" }
                    synonymRepo.updateEmbeddings(entityId, item.termEmbedding, item.descEmbedding)
                }
            }
            SynonymStatus.INDEXED
        } ?: SynonymStatus.FAILED_LLM
    }

    private fun updateStatusIsolated(nodeId: Long, status: SynonymStatus) {
        tx.execute {
            nodeDocRepo.updateSynonymStatus(nodeId, defaultLocale, status.name)
        }
    }

    // --- Логика анализа текста (Heuristics & Prompts) ---

    private fun shouldSkipByHeuristic(methodName: String): Boolean {
        val technicalTokens = listOf("DataSource", "Config", "Adapter", "Bean", "Test", "Properties")
        return technicalTokens.any { methodName.contains(it, ignoreCase = true) }
    }

    private fun isBusinessLogicFastCheck(context: String): Boolean {
        val prompt = "Определи, есть ли в тексте описание бизнес-логики. Ответь только 'YES' или 'NO'. Текст: $context"
        return fastCheckClient.prompt().user(prompt).call().content()?.contains("YES", ignoreCase = true) ?: false
    }

    private fun extractSynonymPairs(nodeName: String, docContent: String): List<SynonymPair> {
        val system = "Ты — Senior Backend Engineer. Генерируй ПРИКЛАДНЫЕ синонимы для методов API в формате JSON."
        val user = "Метод: $nodeName\nДокументация: $docContent"

        return try {
            val response = extractionClient.prompt().system(system).user(user).call().content() ?: ""
            val json = response.replace(Regex("(?s)```json(.*?)```"), "$1").trim()
            val tree = objectMapper.readTree(json)
            if (tree.isArray) {
                tree.mapNotNull { n ->
                    val t = n.get("term")?.asText()?.trim()
                    val d = n.get("description")?.asText()?.trim()
                    if (!t.isNullOrBlank() && !d.isNullOrBlank()) SynonymPair(t, d) else null
                }
            } else emptyList()
        } catch (e: Exception) {
            log.warn("Ошибка парсинга LLM ответа: ${e.message}")
            emptyList()
        }
    }

    private fun isValidPair(pair: SynonymPair, nodeName: String): Boolean {
        val term = pair.term.lowercase().trim()
        return when {
            term.length < 5 -> false
            chineseCharsRegex.containsMatchIn(term) -> false
            term == nodeName.lowercase() -> false
            globalStopTerms.any { term.contains(it) } -> false
            else -> true
        }
    }

    private fun extractPurposeSection(docTech: String): String? =
        Regex("""(?s)##\s+Назначение\s*\n(.*?)(?=\n##|\z)""", RegexOption.IGNORE_CASE)
            .find(docTech)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

    private fun extractContextSections(docTech: String): String {
        return listOf("Назначение", "Контракт", "Поведение").mapNotNull { section ->
            Regex("""(?s)##\s+$section\s*\n(.*?)(?=\n##|\z)""", RegexOption.IGNORE_CASE)
                .find(docTech)?.groupValues?.getOrNull(1)?.trim()?.let { "$section: $it" }
        }.joinToString("\n\n")
    }

    private fun extractContextForJudge(docTech: String): String =
        extractPurposeSection(docTech)?.take(300) ?: "No context"

    // --- Data Classes ---

    private data class SynonymPair(val term: String, val description: String)
    private data class EnrichedSynonym(
        val term: String,
        val description: String,
        val termEmbedding: String,
        val descEmbedding: String
    )
}