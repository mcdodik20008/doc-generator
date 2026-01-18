plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    id("io.spring.dependency-management") version "1.1.7"
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

group = "com.bftcom"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Зависимость на kernel:domain для использования NodeKind, EdgeKind, Lang и других доменных примитивов
    api(projects.kernel.domain)

    // ===== Jackson (Kotlin, JSR310) =====
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // ===== Spring Context (для @Configuration) =====
    implementation("org.springframework:spring-context")

    // ===== Logging (SLF4J) =====
    implementation("org.slf4j:slf4j-api")

    // ===== Testing =====
    testImplementation(kotlin("test"))
    testImplementation("org.assertj:assertj-core:3.26.3")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
