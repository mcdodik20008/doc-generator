// curl http://192.168.0.15:11434/api/ps

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}
val springAiVersion by extra("1.0.3")

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
    implementation(projects.contexts.rag.contextsRagApi)
    implementation(projects.contexts.rag.contextsRagImpl)

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

dependencyManagement {
    imports { mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion") }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.test { useJUnitPlatform() }
