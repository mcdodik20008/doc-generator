package com.bftcom.docgenerator.config

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import javax.sql.DataSource

@Configuration
@Profile("demo")
class DemoPostgresConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    private var pg: EmbeddedPostgres? = null

    @Bean
    @Primary
    fun dataSource(): DataSource {
        log.info("Starting embedded PostgreSQL for demo profile...")
        val embeddedPg =
            EmbeddedPostgres
                .builder()
                .start()
        pg = embeddedPg
        val ds = embeddedPg.postgresDatabase
        log.info("Embedded PostgreSQL started on port {}", embeddedPg.port)
        return ds
    }

    @PreDestroy
    fun stop() {
        pg?.close()
        log.info("Embedded PostgreSQL stopped")
    }
}
