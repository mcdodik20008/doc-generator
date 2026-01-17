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
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.6"))

    implementation(projects.contexts.git.contextsGitApi)
    implementation(projects.contexts.graph.contextsGraphApi)
    implementation(projects.contexts.graph.contextsGraphImpl)
    implementation(projects.kernel.domain)
    implementation(projects.kernel.db)

    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")

    implementation("org.springframework.boot:spring-boot-starter:3.5.6")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.5.6")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.6")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
