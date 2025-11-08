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
    implementation(projects.contexts.git.contextsGitApi)
    implementation(projects.contexts.graph.contextsGraphApi)
    implementation(projects.contexts.graph.contextsGraphImpl)
    implementation(projects.kernel.domain)
    implementation(projects.kernel.db)

    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")

    compileOnly("org.springframework.boot:spring-boot")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
