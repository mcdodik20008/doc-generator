package com.bftcom.docgenerator.chunking.service

import com.bftcom.docgenerator.repo.ChunkRepository
import com.bftcom.docgenerator.repo.NodeRepository
import com.bftcom.docgenerator.repo.EdgeRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class ChunkDetailsService(
    private val chunkRepo: ChunkRepository,
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
) {
    fun getDetails(id: String): ChunkDetailsResponse {
        val chunk = chunkRepo.findByNodeId(id.toLong()).first()

        val nodeId = chunk.node.id ?: return ChunkDetailsResponse(
            id = chunk.id.toString(),
            title = chunk.title,
            node = null,
            content = chunk.content,
            metadata = chunk.pipeline,
            embeddingSize = chunk.emb?.size,
            relations = ChunkRelations(emptyList(), emptyList())
        )

        val node = nodeRepo.findByIdOrNull(nodeId)

        val outgoing = edgeRepo.findAllBySrcId(nodeId)
            .map { e -> RelationBrief(e.src.id.toString(), e.kind.name, e.dst.id.toString()) }

        val incoming = edgeRepo.findAllByDstId(nodeId)
            .map { e -> RelationBrief(e.dst.id.toString(), e.kind.name, e.src.id.toString()) }

        return ChunkDetailsResponse(
            id = chunk.id.toString(),
            title = chunk.title,
            node = node?.let {
                NodeBrief(
                    id = it.id.toString(),
                    kind = it.kind.name,
                    name = it.name ?: "",
                    packageName = it.packageName
                )
            },
            content = chunk.content,
            metadata = chunk.pipeline,
            embeddingSize = chunk.emb?.size,
            relations = ChunkRelations(incoming = incoming, outgoing = outgoing)
        )
    }
}
