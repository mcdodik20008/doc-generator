plugins {
    kotlin("jvm") version "2.0.21"
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

group = "com.bftcom"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(projects.kernel.domain)
    api("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}