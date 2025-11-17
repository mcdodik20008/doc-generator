rootProject.name = "doc-generator"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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
