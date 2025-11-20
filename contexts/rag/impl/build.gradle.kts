plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    id("io.spring.dependency-management") version "1.1.7"
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

val springAiVersion by extra("1.0.3")

group = "com.bftcom"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.contexts.rag.contextsRagApi)
    implementation(projects.contexts.embedding.contextsEmbeddingApi)
    implementation(projects.contexts.ai)

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
    implementation("org.springframework.ai:spring-ai-markdown-document-reader")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    testImplementation(kotlin("test"))
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.6")
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
