plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
}

group = "com.bftcom"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.contexts.graph.api)
    implementation(projects.kernel.domain)
    implementation(projects.kernel.db)

    // === AST/PSI для нашего графового билдера ===
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")
    // Для K2-резолва позже включим строку ниже (и только когда дойдём до него):
    // implementation("org.jetbrains.kotlin:kotlin-analysis-api-standalone:2.0.21")

    // ===== Core / Kotlin / JSON =====
    compileOnly("com.fasterxml.jackson.core:jackson-core:2.17.2")
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    compileOnly("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    compileOnly("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")

    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}