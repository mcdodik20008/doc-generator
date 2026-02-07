package com.bftcom.docgenerator.api.dashboard

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.db.ApplicationStats
import com.bftcom.docgenerator.db.DashboardStatsRepository
import com.bftcom.docgenerator.db.GlobalStats
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class DashboardResponse(
    val global: GlobalStats,
    val applications: List<ApplicationStats>,
)

data class AppBrief(
    val id: Long,
    val key: String,
    val name: String,
)

@RestController
@RequestMapping("/api/dashboard")
class DashboardApiController(
    private val statsRepo: DashboardStatsRepository,
    private val applicationRepo: ApplicationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    fun stats(): DashboardResponse {
        return try {
            val global = statsRepo.findGlobalStats()
            val apps = statsRepo.findApplicationStats()
            DashboardResponse(global = global, applications = apps)
        } catch (e: Exception) {
            log.error("Failed to load dashboard stats", e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load dashboard stats")
        }
    }

    @GetMapping("/applications")
    @Transactional(readOnly = true)
    fun applications(): List<AppBrief> {
        return try {
            applicationRepo.findAll().map { app ->
                AppBrief(id = app.id!!, key = app.key, name = app.name)
            }
        } catch (e: Exception) {
            log.error("Failed to load applications list", e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load applications")
        }
    }
}
