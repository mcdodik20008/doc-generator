package com.bftcom.docgenerator.api.chunk

import com.bftcom.docgenerator.graph.api.dto.GraphResponse
import com.bftcom.docgenerator.chunking.service.ChunkGraphService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/graph/chunks")
class ChunkGraphController(
    private val service: ChunkGraphService,
) {
    @GetMapping
    fun graph(
        @RequestParam applicationId: Long,
        @RequestParam(required = false, defaultValue = "") kinds: List<String>,
        @RequestParam(required = false, defaultValue = "500") limit: Int,
        @RequestParam(required = false, defaultValue = "false") withRelations: Boolean,
    ): GraphResponse = service.buildGraph(applicationId, kinds.toSet().filter { it.isNotBlank() }.toSet(), limit, withRelations)

    @GetMapping("/expand")
    fun expand(
        @RequestParam nodeId: String,
        @RequestParam limit: Int = 200,
    ): GraphResponse = service.expandNode(nodeId, limit)
}
