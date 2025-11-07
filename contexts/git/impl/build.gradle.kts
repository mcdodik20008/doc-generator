plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.bftcom"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.contexts.graph.contextsGraphApi)
    implementation(projects.contexts.graph.contextsGraphImpl)
    implementation(projects.contexts.git.contextsGitApi)
    implementation(projects.kernel.domain)
    implementation(projects.kernel.db)

    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.3.5"))
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
    compileOnly("org.springframework.boot:spring-boot")

    // Для метаданных (опционально, если нужно автодополнение в IDE):
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
