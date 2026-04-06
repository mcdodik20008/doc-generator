package com.bftcom.docgenerator.domain.apikey

// import io.hypersistence.utils.hibernate.type.array.StringArrayType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "api_key", schema = "doc_generator")
class ApiKey(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false)
    var name: String,
    @Column(name = "key_hash", nullable = false, unique = true)
    var keyHash: String,
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "scopes", columnDefinition = "text[]")
    var scopes: Array<String> = emptyArray(),
    @Column(nullable = false)
    var active: Boolean = true,
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "expires_at")
    var expiresAt: OffsetDateTime? = null,
    @Column(name = "last_used_at")
    var lastUsedAt: OffsetDateTime? = null,
) {
    fun isExpired(): Boolean = expiresAt?.isBefore(OffsetDateTime.now()) == true

    fun isValid(): Boolean = active && !isExpired()
}
