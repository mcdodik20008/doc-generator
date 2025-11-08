plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

group = "com.bftcom"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.contexts.graph.contextsGraphApi)
    implementation(projects.kernel.domain)
    implementation(projects.kernel.db)

    // === AST/PSI для парсинга Kotlin кода ===
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")

    // ===== JSON для сериализации/десериализации NodeMeta ====
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // kotlin-reflect нужен для ObjectMapper.convertValue() и рефлексии
    implementation(kotlin("reflect"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}