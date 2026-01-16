package com.bftcom.docgenerator.chunking.nodedoc

import com.bftcom.docgenerator.ai.chatclients.NodeDocDigestClient
import com.bftcom.docgenerator.ai.chatclients.OllamaCoderClient
import com.bftcom.docgenerator.ai.chatclients.OllamaTalkerClient
import com.bftcom.docgenerator.ai.model.TalkerRewriteRequest
import com.bftcom.docgenerator.db.NodeDocRepository
import com.bftcom.docgenerator.domain.node.Node
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
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
    @param:Value("\${docgen.nodedoc.prompt-id:node-doc-v1}")
    private val promptId: String,
) {
    data class GeneratedDoc(
        val docTech: String,
        val docPublic: String,
        val docDigest: String,
        val modelMeta: Map<String, Any>,
    )

    fun generate(node: Node, locale: String): GeneratedDoc {
        val built = contextBuilder.build(node, locale)
        val docTech = coder.generate(built.context)
        val docPublic =
            talker.rewrite(
                TalkerRewriteRequest(
                    nodeFqn = node.fqn,
                    language = locale,
                    rawContent = docTech,
                ),
            )

        val digest = digestClient.generate(node.kind.name, node.fqn, docTech, built.depsForDigest)

        val modelMeta =
            linkedMapOf(
                "prompt_id" to promptId,
                "deps_missing" to built.depsMissing,
                "included" to built.included,
                "source_hashes" to
                    mapOf(
                        "code_hash" to (node.codeHash ?: ""),
                        "doc_comment_hash" to sha256(node.docComment ?: ""),
                    ),
            )

        return GeneratedDoc(
            docTech = docTech,
            docPublic = docPublic,
            docDigest = digest,
            modelMeta = modelMeta,
        )
    }

    fun store(nodeId: Long, locale: String, generated: GeneratedDoc) {
        nodeDocRepo.upsert(
            nodeId = nodeId,
            locale = locale,
            docPublic = generated.docPublic.ifBlank { null },
            docTech = generated.docTech.ifBlank { null },
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

