package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.apikey.ApiKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ApiKeyRepository : JpaRepository<ApiKey, Long> {
    fun findByKeyHash(keyHash: String): ApiKey?

    fun findAllByActiveTrue(): List<ApiKey>
}
