package com.bftcom.docgenerator.api.integration

import com.bftcom.docgenerator.db.LibraryRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/libraries")
class LibraryApiController(
    private val libraryRepository: LibraryRepository,
) {
    @GetMapping
    fun list(): List<LibraryBrief> =
        libraryRepository.findAll().map { LibraryBrief(it.id!!, it.coordinate) }

    data class LibraryBrief(val id: Long, val coordinate: String)
}
