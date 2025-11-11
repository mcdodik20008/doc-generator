plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

group = "com.bftcom"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(projects.kernel.domain)

    api("org.springframework.data:spring-data-jpa:3.3.4")

    compileOnly("org.springframework.data:spring-data-commons:3.3.4")
    compileOnly("org.hibernate.orm:hibernate-core:6.4.2.Final")
    compileOnly("io.hypersistence:hypersistence-utils-hibernate-62:3.7.2")
    compileOnly("jakarta.persistence:jakarta.persistence-api:3.1.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
