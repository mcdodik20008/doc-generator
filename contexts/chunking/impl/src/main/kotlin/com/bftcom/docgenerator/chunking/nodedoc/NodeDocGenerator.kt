package com.bftcom.docgenerator.chunking.nodedoc

import com.bftcom.docgenerator.ai.chatclients.NodeDocDigestClient
import com.bftcom.docgenerator.ai.chatclients.OllamaCoderClient
import com.bftcom.docgenerator.ai.chatclients.OllamaTalkerClient
import com.bftcom.docgenerator.ai.model.TalkerRewriteRequest
import com.bftcom.docgenerator.ai.prompts.NodeDocContextProfile
import com.bftcom.docgenerator.ai.prompts.NodeDocPromptRegistry
import com.bftcom.docgenerator.db.NodeDocRepository
import com.bftcom.docgenerator.domain.node.Node
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest

@Service
class NodeDocGenerator(
    private val contextBuilder: NodeDocContextBuilder,
    private val nodeDocRepo: NodeDocRepository,
    private val coder: OllamaCoderClient,
    private val digestClient: NodeDocDigestClient,
    private val talker: OllamaTalkerClient,
    private val objectMapper: ObjectMapper,
    private val promptRegistry: NodeDocPromptRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    data class GeneratedDoc(
        val docTech: String,
        val docPublic: String,
        val docDigest: String,
        val modelMeta: Map<String, Any>,
    )

    fun generate(
        node: Node,
        locale: String,
    ): GeneratedDoc {
        log.info("nodedoc: Start doc generate for nodeId = ${node.id}, and fqn = ${node.fqn}")
        val built = contextBuilder.build(node, locale)
        val prompt =
            promptRegistry.resolve(
                NodeDocContextProfile(
                    kind = node.kind,
                    hasKdoc = built.hasKdoc,
                    hasCode = built.hasCode,
                    depsCount = built.depsCount,
                    childrenCount = built.childrenCount,
                ),
            )
        val docTech = coder.generate(built.context, prompt.systemPrompt)
        val docPublic =
            talker.rewrite(
                TalkerRewriteRequest(
                    nodeFqn = node.fqn,
                    language = locale,
                    rawContent = docTech,
                ),
            )

        val rawDigest = digestClient.generate(node.kind.name, node.fqn, docTech, built.depsForDigest)
        val digest = if (rawDigest.isNotBlank()) {
            rawDigest
        } else {
            log.warn("nodedoc: digestClient returned blank for nodeId={}, using docTech truncation as fallback", node.id)
            docTech.take(500).ifBlank { "no-content" }
        }

        val modelMeta =
            linkedMapOf(
                "prompt_id" to prompt.id,
                "deps_missing" to built.depsMissing,
                "included" to built.included,
                "source_hashes" to
                    mapOf(
                        "code_hash" to (node.codeHash ?: ""),
                        "doc_comment_hash" to sha256(node.docComment ?: ""),
                    ),
            )

        log.info("nodedoc: End doc generate for nodeId = ${node.id}, and fqn = ${node.fqn}")
        return GeneratedDoc(
            docTech = docTech,
            docPublic = docPublic,
            docDigest = digest,
            modelMeta = modelMeta,
        )
    }

    fun store(nodeId: Long, locale: String, generated: GeneratedDoc) {
        val docPublic = generated.docPublic.ifBlank { null }
        val docTech = generated.docTech.ifBlank { null }
        if (docPublic == null) {
            log.warn("nodedoc: doc_public is blank for nodeId={}, docTech length={}", nodeId, docTech?.length ?: 0)
        }
        nodeDocRepo.upsert(
            nodeId = nodeId,
            locale = locale,
            docPublic = docPublic,
            docTech = docTech,
            docDigest = generated.docDigest.ifBlank { null },
            modelMetaJson = objectMapper.writeValueAsString(generated.modelMeta),
        )
    }

    private fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
