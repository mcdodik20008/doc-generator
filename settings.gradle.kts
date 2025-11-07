rootProject.name = "doc-generator"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":contexts:chunking:api")
project(":contexts:chunking:api").projectDir = file("contexts/chunking/api")
project(":contexts:chunking:api").name = "contexts-chunking-api"

include(":contexts:graph:api")
project(":contexts:graph:api").projectDir = file("contexts/graph/api")
project(":contexts:graph:api").name = "contexts-graph-api"

include(":contexts:graph:impl")
project(":contexts:graph:impl").projectDir = file("contexts/graph/impl")

include(":kernel:domain")
project(":kernel:domain").projectDir = file("kernel/domain")

include(":kernel:db")
project(":kernel:db").projectDir = file("kernel/db")