rootProject.name = "doc-generator"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
    }
}

include(":contexts:chunking:api")
project(":contexts:chunking:api").projectDir = file("contexts/chunking/api")
project(":contexts:chunking:api").name = "contexts-chunking-api"

include(":contexts:graph:api")
project(":contexts:graph:api").projectDir = file("contexts/graph/api")
project(":contexts:graph:api").name = "contexts-graph-api"

include(":contexts:chunking:impl")
project(":contexts:chunking:impl").projectDir = file("contexts/chunking/impl")
project(":contexts:chunking:impl").name = "contexts-chunking-impl"

include(":contexts:graph:impl")
project(":contexts:graph:impl").projectDir = file("contexts/graph/impl")
project(":contexts:graph:impl").name = "contexts-graph-impl"

include("contexts:ai")
project(":contexts:ai").projectDir = file("contexts/ai")

include("contexts:git:api")
project(":contexts:git:api").projectDir = file("contexts/git/api")
project(":contexts:git:api").name = "contexts-git-api"

include("contexts:git:impl")
project(":contexts:git:impl").projectDir = file("contexts/git/impl")
project(":contexts:git:impl").name = "contexts-git-impl"

include("contexts:postprocess")
project(":contexts:postprocess").projectDir = file("contexts/postprocess")

include(":contexts:embedding:api")
project(":contexts:embedding:api").projectDir = file("contexts/embedding/api")
project(":contexts:embedding:api").name = "contexts-embedding-api"

include(":contexts:embedding:impl")
project(":contexts:embedding:impl").projectDir = file("contexts/embedding/impl")
project(":contexts:embedding:impl").name = "contexts-embedding-impl"

include(":contexts:library:api")
project(":contexts:library:api").projectDir = file("contexts/library/api")
project(":contexts:library:api").name = "contexts-library-api"

include(":contexts:library:impl")
project(":contexts:library:impl").projectDir = file("contexts/library/impl")
project(":contexts:library:impl").name = "contexts-library-impl"

include(":kernel:domain")
project(":kernel:domain").projectDir = file("kernel/domain")

include(":kernel:db")
project(":kernel:db").projectDir = file("kernel/db")

include(":contexts:rag:api")
project(":contexts:rag:api").projectDir = file("contexts/rag/api")
project(":contexts:rag:api").name = "contexts-rag-api"

include(":contexts:rag:impl")
project(":contexts:rag:impl").projectDir = file("contexts/rag/impl")
project(":contexts:rag:impl").name = "contexts-rag-impl"
