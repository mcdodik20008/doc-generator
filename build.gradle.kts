plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

val springBootVersion = "3.5.6"

subprojects {
    repositories {
        mavenCentral()
    }
    apply(plugin = "org.jetbrains.kotlinx.kover")
    apply(plugin = "io.spring.dependency-management")

    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
        }
    }
}

// curl http://192.168.0.15:11434/api/ps

group = "com.bftcom"
version = "0.0.1-SNAPSHOT"
description = "doc-generator"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // === Modules ===
    // implementation() - подключаем все модули
    // Runtime автоматически подтянет все транзитивные зависимости через api() зависимости модулей
    implementation(projects.contexts.graph.contextsGraphApi)
    implementation(projects.contexts.graph.contextsGraphImpl)
    implementation(projects.contexts.chunking.contextsChunkingApi)
    implementation(projects.contexts.chunking.contextsChunkingImpl)
    implementation(projects.contexts.ai)
    implementation(projects.contexts.git.contextsGitApi)
    implementation(projects.contexts.git.contextsGitImpl)
    implementation(projects.contexts.library.contextsLibraryApi)
    implementation(projects.contexts.library.contextsLibraryImpl)
    implementation(projects.contexts.postprocess)
    implementation(projects.contexts.embedding.contextsEmbeddingApi)
    implementation(projects.contexts.embedding.contextsEmbeddingImpl)
    implementation(projects.kernel.domain)
    implementation(projects.kernel.db)
    implementation(projects.kernel.shared)
    implementation(projects.contexts.rag.contextsRagApi)
    implementation(projects.contexts.rag.contextsRagImpl)

    kover(projects.contexts.graph.contextsGraphApi)
    kover(projects.contexts.graph.contextsGraphImpl)
    kover(projects.contexts.chunking.contextsChunkingApi)
    kover(projects.contexts.chunking.contextsChunkingImpl)
    kover(projects.contexts.ai)
    kover(projects.contexts.git.contextsGitApi)
    kover(projects.contexts.git.contextsGitImpl)
    kover(projects.contexts.library.contextsLibraryApi)
    kover(projects.contexts.library.contextsLibraryImpl)
    kover(projects.contexts.postprocess)
    kover(projects.contexts.embedding.contextsEmbeddingApi)
    kover(projects.contexts.embedding.contextsEmbeddingImpl)
    kover(projects.kernel.domain)
    kover(projects.kernel.db)
    kover(projects.kernel.shared)
    kover(projects.contexts.rag.contextsRagApi)
    kover(projects.contexts.rag.contextsRagImpl)

    // ===== Core / Kotlin / JSON =====
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // ===== Spring Web / Validation / Observability =====
    // Эти зависимости нужны только в root модуле для веб-приложения
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.6.0")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.3.1")

    // ===== DB / Migrations =====
    implementation("io.hypersistence:hypersistence-utils-hibernate-62:3.7.2")
    implementation("org.postgresql:postgresql")
    implementation("org.liquibase:liquibase-core")

    // ===== Spring AI =====
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
    implementation("org.springframework.ai:spring-ai-markdown-document-reader")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // ===== Dev-only =====
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // ===== Testing =====
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
}

kover {
    reports {
        // Настройки исключений применяются ко всем отчетам
        filters {
            excludes {
                // 1. Исключение по именам классов (Wildcard по FQCN)
                // Kover сопоставляет это с полным именем класса.
                // Использование "*ClassName" безопасно, если вы уверены в суффиксах.
                classes(
                    "*Dto",
                    "*DTO",
                    "*Request",
                    "*Response",
                    "*Result",
                    "*ApplicationKt" // В Kotlin main-класс часто компилируется в NameKt
                )

                // 2. Исключение пакетов
                // Важно: паттерн "com.example.dto.*" исключит классы в этом пакете,
                // но для вложенных пакетов может потребоваться "**" в зависимости от версии.
                // В Kover 0.7.x+ паттерн "com.pkg.*" обычно рекурсивен.
                packages(
                    "*.dto",
                    "*.config",
                    "*.configprops",
                    "*.domain.dto",
                    "*.domain.nodedoc",
                    "*.domain.chunk",
                    "*.chunking.model",
                    "*.chunking.model.chunk",
                    "*.chunking.model.plan",
                    "*.linker.model",
                    // Исключаем API DTO пакеты из корневого модуля (явные паттерны для надежности)
                    "com.bftcom.docgenerator.api.rag.dto",
                    "com.bftcom.docgenerator.api.embedding.dto",
                    "com.bftcom.docgenerator.api.ingest.dto"
                )

                // 3. Аннотации (Самый надежный инженерный подход)
                // Вместо того чтобы гадать с именами пакетов, можно исключить всё,
                // что помечено определенной аннотацией (например, собственной @Generated).
                annotatedBy("org.springframework.boot.context.properties.ConfigurationProperties")
            }
        }

        total {
            log {
                onCheck = true
                header = "========== TOTAL PROJECT COVERAGE =========="
                format = "Final Coverage: {percentage}%"
            }
        }
    }

    // Quality Gates: Запрещаем падение покрытия ниже текущего уровня
    // TODO: Настроить правильный синтаксис verify для версии 0.9.4
    // В версии 0.9.4 API может отличаться, нужно проверить документацию
    // verify {
    //     rule {
    //         name = "Minimum Line Coverage"
    //         bound {
    //             minValue = 50.0 // Текущий уровень покрытия (52.9%)
    //             coverageUnit = kotlinx.kover.api.CoverageUnit.LINE
    //             aggregation = kotlinx.kover.api.AggregationType.COVERED_PERCENTAGE
    //         }
    //     }
    //     rule {
    //         name = "Minimum Branch Coverage"
    //         bound {
    //             minValue = 40.0 // Минимальный порог для Branch Coverage
    //             coverageUnit = kotlinx.kover.api.CoverageUnit.BRANCH
    //             aggregation = kotlinx.kover.api.AggregationType.COVERED_PERCENTAGE
    //         }
    //     }
    // }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.test { useJUnitPlatform() }
