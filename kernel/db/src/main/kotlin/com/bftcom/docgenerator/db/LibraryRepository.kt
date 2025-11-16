package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.library.Library
import org.springframework.data.jpa.repository.JpaRepository

interface LibraryRepository : JpaRepository<Library, Long> {
    fun findByCoordinate(coordinate: String): Library?
}


