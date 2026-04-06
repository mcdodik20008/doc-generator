plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
    id("io.spring.dependency-management") version "1.1.7"
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

group = "com.bftcom"
version = "0.0.1-SNAPSHOT"
val jacksonVersion = "2.17.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.contexts.graph.contextsGraphApi)
    implementation(projects.contexts.library.contextsLibraryApi)
    implementation(projects.kernel.domain)
    implementation(projects.kernel.db)
    implementation(projects.kernel.shared)

    // === AST/PSI для парсинга Kotlin кода ===
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")

    // === YAML parsing для YamlConfigScanner ===
    implementation("org.yaml:snakeyaml")

    // === Java source parsing ===
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.26.2")

    // === Proto file parsing ===
    implementation("com.squareup.wire:wire-schema:5.1.0")

    // ===== JSON для сериализации/десериализации =====
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // kotlin-reflect нужен для ObjectMapper.convertValue() и рефлексии
    implementation(kotlin("reflect"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
