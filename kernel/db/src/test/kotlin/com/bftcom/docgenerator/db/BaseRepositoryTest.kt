package com.bftcom.docgenerator.db

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@DataJpaTest
@TestPropertySource(properties = [
    "spring.liquibase.default-schema=doc_generator",
    "spring.liquibase.liquibase-schema=doc_generator",
    "spring.liquibase.change-log=classpath:liquibase/db.changelog-master.xml"
])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = [BaseRepositoryTest.Companion.Initializer::class])
@EntityScan(basePackages = ["com.bftcom.docgenerator.domain"])
abstract class BaseRepositoryTest {

    companion object {
        private val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("pgvector/pgvector:pg16")).apply {
            withDatabaseName("doc_generator")
            withUsername("test")
            withPassword("test")
            withInitScript("init_schema.sql")
            start()
        }

        open class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
            override fun initialize(ctx: ConfigurableApplicationContext) {
                TestPropertyValues.of(
                    "spring.datasource.url=${postgres.jdbcUrl}",
                    "spring.datasource.username=${postgres.username}",
                    "spring.datasource.password=${postgres.password}",

                    // Отключаем ddl-auto, так как теперь рулит Liquibase
                    "spring.jpa.hibernate.ddl-auto=none",

                    // Настройка Liquibase
                    "spring.liquibase.enabled=true",
                    // Путь к мастер-файлу из модуля domain (Gradle его видит через api проект)
                    "spring.liquibase.change-log=classpath:liquibase/db.changelog-master.xml",
                    "spring.liquibase.default-schema=doc_generator",
                    "spring.liquibase.liquibase-schema=doc_generator"
                ).applyTo(ctx.environment)
            }
        }
    }
}