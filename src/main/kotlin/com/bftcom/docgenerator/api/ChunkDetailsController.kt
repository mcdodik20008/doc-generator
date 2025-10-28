package com.bftcom.docgenerator.api

import com.bftcom.docgenerator.chunking.service.ChunkDetailsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/chunks")
class ChunkDetailsController(
    private val chunkService: ChunkDetailsService,
) {
    @GetMapping("/{id}")
    fun details(
        @PathVariable id: String,
    ) = chunkService.getDetails(id)
}
