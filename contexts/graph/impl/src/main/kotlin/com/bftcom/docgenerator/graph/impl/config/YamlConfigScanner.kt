package com.bftcom.docgenerator.graph.impl.config

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.extension

/**
 * Сканирует YAML-конфигурацию приложения (application*.yml, bootstrap*.yml)
 * и создает INFRASTRUCTURE-ноды для обнаруженных инфраструктурных зависимостей.
 */
@Component
class YamlConfigScanner(
    private val nodeRepo: NodeRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val yaml = Yaml()

    /**
     * Сканирует YAML-файлы в sourceRoot и создает INFRASTRUCTURE-ноды.
     * @return количество созданных нод
     */
    fun scan(application: Application, sourceRoot: Path): Int {
        val yamlFiles = findYamlFiles(sourceRoot)
        if (yamlFiles.isEmpty()) {
            log.debug("No YAML config files found under {}", sourceRoot)
            return 0
        }

        log.info("Found {} YAML config files to scan", yamlFiles.size)

        // Собираем свойства из всех файлов (позже найденные перезаписывают)
        val allProps = mutableMapOf<String, String>()
        val yamlSnippets = mutableMapOf<String, String>() // groupKey → yaml snippet

        for (yamlFile in yamlFiles) {
            try {
                val fileProps = parseYamlFile(yamlFile)
                allProps.putAll(fileProps)
                log.debug("Parsed {} properties from {}", fileProps.size, yamlFile.name)
            } catch (e: Exception) {
                log.warn("Failed to parse YAML file {}: {}", yamlFile, e.message)
            }
        }

        if (allProps.isEmpty()) {
            log.debug("No properties found in YAML files")
            return 0
        }

        // Детектируем интеграции
        val integrations = IntegrationDetector.detect(allProps)
        log.info("Detected {} infrastructure integrations", integrations.size)

        // Создаём INFRASTRUCTURE-ноды
        var created = 0
        for (integration in integrations) {
            try {
                val node = createInfrastructureNode(application, integration, yamlFiles)
                if (node != null) created++
            } catch (e: Exception) {
                log.warn(
                    "Failed to create INFRASTRUCTURE node for {}: {}",
                    integration.groupKey, e.message,
                )
            }
        }

        log.info("Created {} INFRASTRUCTURE nodes for app [id={}]", created, application.id)
        return created
    }

    /**
     * Ищет YAML-файлы конфигурации в sourceRoot.
     */
    internal fun findYamlFiles(sourceRoot: Path): List<Path> {
        val result = mutableListOf<Path>()

        try {
            Files.walk(sourceRoot)
                .filter { path ->
                    val name = path.name
                    val ext = path.extension
                    (ext == "yml" || ext == "yaml") &&
                        (name.startsWith("application") || name.startsWith("bootstrap"))
                }
                .filter { path ->
                    // Должен быть в src/main/resources или resources корне
                    val pathStr = path.toString().replace('\\', '/')
                    pathStr.contains("src/main/resources") || pathStr.contains("resources/")
                }
                .forEach { result.add(it) }
        } catch (e: Exception) {
            log.warn("Error walking source root {}: {}", sourceRoot, e.message)
        }

        return result
    }

    /**
     * Парсит один YAML-файл в плоскую карту "dot.separated.key" → "value".
     */
    internal fun parseYamlFile(yamlFile: Path): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val content = Files.readString(yamlFile)

        // SnakeYAML может вернуть несколько документов (---), обрабатываем все
        yaml.loadAll(content).forEach { doc ->
            if (doc is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                flattenMap("", doc as Map<String, Any>, result)
            }
        }

        return result
    }

    /**
     * Рекурсивно «раскладывает» вложенную карту в плоскую.
     */
    private fun flattenMap(prefix: String, map: Map<String, Any>, result: MutableMap<String, String>) {
        for ((key, value) in map) {
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    flattenMap(fullKey, value as Map<String, Any>, result)
                }
                is List<*> -> {
                    value.forEachIndexed { idx, item ->
                        if (item is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            flattenMap("$fullKey[$idx]", item as Map<String, Any>, result)
                        } else {
                            result["$fullKey[$idx]"] = item?.toString() ?: ""
                        }
                    }
                }
                else -> result[fullKey] = value.toString()
            }
        }
    }

    private fun createInfrastructureNode(
        application: Application,
        integration: DetectedIntegration,
        yamlFiles: List<Path>,
    ): Node? {
        val fqn = "infra:yaml:${integration.type.name.lowercase()}:${integration.groupKey}"

        // Проверяем, не существует ли уже
        val appId = requireNotNull(application.id)
        val existing = nodeRepo.findByApplicationIdAndFqn(appId, fqn)
        if (existing != null) {
            log.debug("INFRASTRUCTURE node already exists: {}", fqn)
            return null
        }

        val readableName = IntegrationDetector.readableName(integration.groupKey, integration.type)

        // Собираем YAML-snippet для контекста
        val yamlSnippet = buildYamlSnippet(integration)

        val meta = mutableMapOf<String, Any>(
            "source" to "yaml",
            "configPrefix" to integration.groupKey,
            "integrationType" to integration.type.name,
            "synthetic" to true,
        )

        integration.defaultUrl?.let { meta["defaultUrl"] = it }
        integration.envVar?.let { meta["envVar"] = it }

        if (integration.properties.isNotEmpty()) {
            meta["properties"] = integration.properties
        }

        val node = Node(
            application = application,
            fqn = fqn,
            name = readableName,
            packageName = null,
            kind = NodeKind.INFRASTRUCTURE,
            lang = Lang.yaml,
            parent = null,
            filePath = yamlFiles.firstOrNull()?.toString(),
            lineStart = null,
            lineEnd = null,
            sourceCode = yamlSnippet,
            docComment = null,
            signature = null,
            codeHash = null,
            meta = meta,
        )

        return nodeRepo.save(node)
    }

    private fun buildYamlSnippet(integration: DetectedIntegration): String {
        val sb = StringBuilder()
        sb.appendLine("# ${integration.type.name}: ${integration.groupKey}")
        for ((key, value) in integration.properties.entries.take(20)) {
            sb.appendLine("$key: $value")
        }
        if (integration.properties.size > 20) {
            sb.appendLine("# ... and ${integration.properties.size - 20} more properties")
        }
        return sb.toString().trimEnd()
    }
}
