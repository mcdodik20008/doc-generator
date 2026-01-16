plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

group = "com.bftcom"
version = "0.0.1-SNAPSHOT"
val jacksonVersion = "2.17.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.contexts.chunking.contextsChunkingApi)
    implementation(projects.kernel.domain)
    implementation(projects.kernel.db)
    implementation(projects.contexts.ai)

    // Jackson для сериализации JSON (model_meta, chunk.metadata)
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.13")
    // Нужен для загрузки ChatClient в unit-тестах (Ollama*Client использует Spring AI)
    testImplementation("org.springframework.ai:spring-ai-starter-model-ollama:1.0.3")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
