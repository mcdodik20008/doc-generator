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
    implementation(projects.contexts.graph.contextsGraphApi)
    implementation(projects.contexts.library.contextsLibraryApi)
    implementation(projects.kernel.domain)
    implementation(projects.kernel.db)

    // === AST/PSI для парсинга Kotlin кода ===
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")

    // ===== JSON для сериализации/десериализации NodeMeta ====
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // kotlin-reflect нужен для ObjectMapper.convertValue() и рефлексии
    implementation(kotlin("reflect"))

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
