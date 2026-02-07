package com.bftcom.docgenerator.chunking.service

import com.bftcom.docgenerator.chunking.model.chunk.ChunkDetailsResponse
import com.bftcom.docgenerator.chunking.model.chunk.ChunkRelations
import com.bftcom.docgenerator.chunking.model.chunk.NodeBrief
import com.bftcom.docgenerator.chunking.model.chunk.RelationBrief
import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class ChunkDetailsService(
    private val chunkRepo: ChunkRepository,
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
) {
    fun getDetails(id: String): ChunkDetailsResponse {
        val nodeIdLong = id.toLongOrNull()
            ?: return emptyDetails(id)

        val chunk = chunkRepo.findByNodeId(nodeIdLong).firstOrNull()
            ?: return nodeOnlyDetails(id, nodeIdLong)

        val nodeId =
            chunk.node.id ?: return ChunkDetailsResponse(
                id = chunk.id.toString(),
                title = "${chunk.source}:${chunk.kind ?: "unknown"}",
                node = null,
                content = chunk.content,
                metadata = chunk.metadata,
                embeddingSize = chunk.emb?.size,
                relations = ChunkRelations(emptyList(), emptyList()),
            )

        val node = nodeRepo.findByIdOrNull(nodeId)

        val outgoing =
            edgeRepo
                .findAllBySrcId(nodeId)
                .map { e -> RelationBrief(e.src.id.toString(), e.kind.name, e.dst.id.toString()) }

        val incoming =
            edgeRepo
                .findAllByDstId(nodeId)
                .map { e -> RelationBrief(e.dst.id.toString(), e.kind.name, e.src.id.toString()) }

        return ChunkDetailsResponse(
            id = chunk.id.toString(),
            title = node?.fqn ?: "${chunk.source}:${chunk.kind ?: "unknown"}",
            node =
                node?.let {
                    NodeBrief(
                        id = it.id.toString(),
                        kind = it.kind.name,
                        name = it.name ?: "",
                        packageName = it.packageName,
                    )
                },
            content = chunk.content,
            metadata = chunk.metadata,
            embeddingSize = chunk.emb?.size,
            relations = ChunkRelations(incoming = incoming, outgoing = outgoing),
        )
    }

    private fun nodeOnlyDetails(id: String, nodeIdLong: Long): ChunkDetailsResponse {
        val node = nodeRepo.findByIdOrNull(nodeIdLong)
            ?: return emptyDetails(id)

        val nodeId = node.id ?: return emptyDetails(id)

        val outgoing = edgeRepo.findAllBySrcId(nodeId)
            .map { e -> RelationBrief(e.src.id.toString(), e.kind.name, e.dst.id.toString()) }
        val incoming = edgeRepo.findAllByDstId(nodeId)
            .map { e -> RelationBrief(e.dst.id.toString(), e.kind.name, e.src.id.toString()) }

        return ChunkDetailsResponse(
            id = nodeId.toString(),
            title = node.fqn,
            node = NodeBrief(
                id = nodeId.toString(),
                kind = node.kind.name,
                name = node.name ?: "",
                packageName = node.packageName,
            ),
            content = node.docComment ?: node.signature,
            metadata = node.meta,
            embeddingSize = null,
            relations = ChunkRelations(incoming = incoming, outgoing = outgoing),
        )
    }

    private fun emptyDetails(id: String) = ChunkDetailsResponse(
        id = id,
        title = "Node $id",
        node = null,
        content = null,
        metadata = null,
        embeddingSize = null,
        relations = ChunkRelations(emptyList(), emptyList()),
    )
}
