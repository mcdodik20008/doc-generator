package com.bftcom.docgenerator.chunking.nodedoc

import com.bftcom.docgenerator.ai.chatclients.NodeDocDigestClient
import com.bftcom.docgenerator.ai.chatclients.NodeDocTechClient
import com.bftcom.docgenerator.ai.chatclients.OllamaTalkerClient
import com.bftcom.docgenerator.ai.model.TalkerRewriteRequest
import com.bftcom.docgenerator.db.NodeDocRepository
import com.bftcom.docgenerator.domain.node.Node
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest

@Service
class NodeDocGenerator(
    private val contextBuilder: NodeDocContextBuilder,
    private val nodeDocRepo: NodeDocRepository,
    private val techClient: NodeDocTechClient,
    private val digestClient: NodeDocDigestClient,
    private val talker: OllamaTalkerClient,
    @param:Value("\${docgen.nodedoc.prompt-id:node-doc-v1}")
    private val promptId: String,
) {
    fun generateAndStore(node: Node, locale: String) {
        val built = contextBuilder.build(node, locale)
        val docTech = techClient.generate(built.context)
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
            linkedMapOf<String, Any>(
                "prompt_id" to promptId,
                "deps_missing" to built.depsMissing,
                "included" to built.included,
                "source_hashes" to
                    mapOf(
                        "code_hash" to (node.codeHash ?: ""),
                        "doc_comment_hash" to sha256(node.docComment ?: ""),
                    ),
            )

        nodeDocRepo.upsert(
            nodeId = node.id ?: error("node.id is null"),
            locale = locale,
            docPublic = docPublic.ifBlank { null },
            docTech = docTech.ifBlank { null },
            docDigest = digest.ifBlank { null },
            modelMetaJson = JsonLite.stringify(modelMeta),
        )
    }

    private fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

