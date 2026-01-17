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
    api(platform("org.springframework.boot:spring-boot-dependencies:3.5.6"))

    api(projects.kernel.domain)
    api(projects.kernel.db)

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}
