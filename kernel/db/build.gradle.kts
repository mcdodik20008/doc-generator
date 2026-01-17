plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
    id("io.spring.dependency-management") version "1.1.7"
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

group = "com.bftcom"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(projects.kernel.domain)

    // ===== Core JPA & Hibernate =====
    // Добавляем Liquibase для миграций в тестах
    implementation("org.liquibase:liquibase-core")
    // Использовать стартер — это Best Practice. Он сам подтянет нужные версии Hibernate и Jakarta API
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    // Поддержка JSONB
    implementation("io.hypersistence:hypersistence-utils-hibernate-62:3.7.2")

    // Рефлексия Kotlin (Критично для работы JPA с Data-классами)
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // ===== JSON Mapping =====
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // ===== DB Driver =====
    runtimeOnly("org.postgresql:postgresql")

    // ===== Testing =====
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.mockk:mockk:1.13.13")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.6")
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
