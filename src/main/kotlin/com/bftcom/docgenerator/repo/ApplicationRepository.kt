package com.bftcom.docgenerator.repo

import com.bftcom.docgenerator.domain.application.Application
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ApplicationRepository : JpaRepository<Application, Long> {
    fun findByKey(key: String): Application?

    fun existsByKey(key: String): Boolean
}
