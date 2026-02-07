package com.bftcom.docgenerator.db

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class ApplicationStats(
    val applicationId: Long,
    val applicationKey: String,
    val applicationName: String,
    val description: String?,
    val repoUrl: String?,
    val repoProvider: String?,
    val defaultBranch: String,
    val lastIndexedAt: OffsetDateTime?,
    val lastIndexStatus: String?,
    val lastIndexError: String?,
    val lastCommitSha: String?,
    val languages: List<String>,
    val tags: List<String>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val nodeCount: Long,
    val chunkCount: Long,
    val edgeCount: Long,
    val synonymCount: Long,
)

data class GlobalStats(
    val totalApplications: Long,
    val totalNodes: Long,
    val totalChunks: Long,
    val totalEdges: Long,
)

@Repository
open class DashboardStatsRepository(
    private val em: EntityManager,
) {
    fun findApplicationStats(): List<ApplicationStats> {
        val sql = """
            SELECT
                a.id,
                a.key,
                a.name,
                a.description,
                a.repo_url,
                a.repo_provider,
                a.default_branch,
                a.last_indexed_at,
                a.last_index_status,
                a.last_index_error,
                a.last_commit_sha,
                a.languages,
                a.tags,
                a.created_at,
                a.updated_at,
                COALESCE(nc.cnt, 0),
                COALESCE(cc.cnt, 0),
                COALESCE(ec.cnt, 0),
                COALESCE(sc.cnt, 0)
            FROM doc_generator.application a
            LEFT JOIN LATERAL (
                SELECT COUNT(*) AS cnt FROM doc_generator.node n WHERE n.application_id = a.id
            ) nc ON true
            LEFT JOIN LATERAL (
                SELECT COUNT(*) AS cnt FROM doc_generator.chunk c WHERE c.application_id = a.id
            ) cc ON true
            LEFT JOIN LATERAL (
                SELECT COUNT(*) AS cnt
                FROM doc_generator.edge e
                JOIN doc_generator.node ns ON e.src_id = ns.id
                WHERE ns.application_id = a.id
            ) ec ON true
            LEFT JOIN LATERAL (
                SELECT COUNT(*) AS cnt
                FROM doc_generator.synonym_dictionary sd
                JOIN doc_generator.node nsyn ON sd.source_node_id = nsyn.id
                WHERE nsyn.application_id = a.id
            ) sc ON true
            ORDER BY a.name
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val rows = em.createNativeQuery(sql).resultList as List<Array<Any?>>

        return rows.map { r ->
            ApplicationStats(
                applicationId = (r[0] as Number).toLong(),
                applicationKey = r[1] as String,
                applicationName = r[2] as String,
                description = r[3] as? String,
                repoUrl = r[4] as? String,
                repoProvider = r[5] as? String,
                defaultBranch = r[6] as String,
                lastIndexedAt = toOffsetDateTime(r[7]),
                lastIndexStatus = r[8] as? String,
                lastIndexError = r[9] as? String,
                lastCommitSha = r[10] as? String,
                languages = sqlArrayToList(r[11]),
                tags = sqlArrayToList(r[12]),
                createdAt = toOffsetDateTime(r[13])!!,
                updatedAt = toOffsetDateTime(r[14])!!,
                nodeCount = (r[15] as Number).toLong(),
                chunkCount = (r[16] as Number).toLong(),
                edgeCount = (r[17] as Number).toLong(),
                synonymCount = (r[18] as Number).toLong(),
            )
        }
    }

    fun findGlobalStats(): GlobalStats {
        val sql = """
            SELECT
                (SELECT COUNT(*) FROM doc_generator.application),
                (SELECT COUNT(*) FROM doc_generator.node),
                (SELECT COUNT(*) FROM doc_generator.chunk),
                (SELECT COUNT(*) FROM doc_generator.edge)
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val row = em.createNativeQuery(sql).singleResult as Array<Any?>
        return GlobalStats(
            totalApplications = (row[0] as Number).toLong(),
            totalNodes = (row[1] as Number).toLong(),
            totalChunks = (row[2] as Number).toLong(),
            totalEdges = (row[3] as Number).toLong(),
        )
    }

    private fun toOffsetDateTime(value: Any?): OffsetDateTime? = when (value) {
        null -> null
        is OffsetDateTime -> value
        is Instant -> value.atOffset(ZoneOffset.UTC)
        else -> throw IllegalArgumentException("Cannot convert ${value.javaClass} to OffsetDateTime")
    }

    private fun sqlArrayToList(value: Any?): List<String> {
        if (value == null) return emptyList()
        if (value is java.sql.Array) {
            return (value.array as? Array<*>)?.filterIsInstance<String>() ?: emptyList()
        }
        if (value is Array<*>) {
            return value.filterIsInstance<String>()
        }
        return emptyList()
    }
}
