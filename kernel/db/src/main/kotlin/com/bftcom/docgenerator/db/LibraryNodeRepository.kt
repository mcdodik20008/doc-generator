package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.library.LibraryNode
import org.springframework.data.jpa.repository.JpaRepository

interface LibraryNodeRepository : JpaRepository<LibraryNode, Long>


