package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.application.Application
import org.springframework.data.jpa.repository.JpaRepository

interface ApplicationRepository : JpaRepository<Application, Long> {
    fun findByKey(key: String): Application?

    fun existsByKey(key: String): Boolean
}
