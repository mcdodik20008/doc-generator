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
    // 1. Определяем список всех внутренних модулей, которые должны войти в состав приложения
    val internalProjects = listOf(
        projects.contexts.graph.contextsGraphApi,
        projects.contexts.graph.contextsGraphImpl,
        projects.contexts.chunking.contextsChunkingApi,
        projects.contexts.chunking.contextsChunkingImpl,
        projects.contexts.ai,
        projects.contexts.git.contextsGitApi,
        projects.contexts.git.contextsGitImpl,
        projects.contexts.library.contextsLibraryApi,
        projects.contexts.library.contextsLibraryImpl,
        projects.contexts.postprocess,
        projects.contexts.embedding.contextsEmbeddingApi,
        projects.contexts.embedding.contextsEmbeddingImpl,
        projects.kernel.domain,
        projects.kernel.db,
        projects.kernel.shared,
        projects.contexts.rag.contextsRagApi,
        projects.contexts.rag.contextsRagImpl
    )
    internalProjects.forEach {
        implementation(it)
        kover(it)
    }

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
    implementation("org.springframework.boot:spring-boot-starter-aop") // Для поддержки @Aspect (rate limiting)
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
                // 1. Исключение по именам классов
                classes(
                    "*Dto",
                    "*DTO",
                    "*Request",
                    "*Response",
                    "*Result",
                    "*ApplicationKt"
                )

                // 2. Исключение пакетов
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
                    "com.bftcom.docgenerator.api.rag.dto",
                    "com.bftcom.docgenerator.api.embedding.dto",
                    "com.bftcom.docgenerator.api.ingest.dto"
                )

                // 3. Аннотации
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
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.test { useJUnitPlatform() }
