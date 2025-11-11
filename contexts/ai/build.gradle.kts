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
    compileOnly("org.springframework.ai:spring-ai-starter-model-ollama")
    compileOnly("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    compileOnly("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
    compileOnly("org.springframework.ai:spring-ai-markdown-document-reader")
    compileOnly("org.springframework.ai:spring-ai-starter-model-openai")

    // compileOnly - только для аннотаций валидации
    compileOnly("jakarta.validation:jakarta.validation-api:3.1.0")
    // compileOnly - только для интерфейсов HTTP клиента
    compileOnly("org.apache.httpcomponents.client5:httpclient5:5.3.1")

    testImplementation(kotlin("test"))
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
