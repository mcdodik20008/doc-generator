plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    `java-library`
    id("io.spring.dependency-management") version "1.1.7"
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

group = "com.bftcom"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(platform("org.springframework.ai:spring-ai-bom:1.0.3"))
    
    implementation(projects.kernel.domain)

    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
    implementation("org.springframework.ai:spring-ai-markdown-document-reader")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // compileOnly - только для аннотаций валидации
    compileOnly("jakarta.validation:jakarta.validation-api:3.1.0")
    // compileOnly - только для интерфейсов HTTP клиента
    compileOnly("org.apache.httpcomponents.client5:httpclient5:5.3.1")

    testImplementation(kotlin("test"))
    testImplementation("org.assertj:assertj-core:3.26.3")

    // Исправляем Mockito: добавляем расширение для JUnit 5
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.12.0") // НЕДОСТАЮЩАЯ ДЛЯ MockitoExtension
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
