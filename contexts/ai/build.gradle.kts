plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
    id("io.spring.dependency-management") version "1.1.7"
}
val springAiVersion by extra("1.0.3")

group = "com.bftcom"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    compileOnly("jakarta.validation:jakarta.validation-api:3.1.0")
    compileOnly("org.apache.httpcomponents.client5:httpclient5:5.3.1")

    compileOnly("org.springframework.ai:spring-ai-starter-model-ollama")
    compileOnly("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    compileOnly("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
    compileOnly("org.springframework.ai:spring-ai-markdown-document-reader")
    compileOnly("org.springframework.ai:spring-ai-starter-model-openai")
}

dependencyManagement {
    imports { mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion") }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}