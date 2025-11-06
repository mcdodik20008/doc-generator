rootProject.name = "doc-generator"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

//include(":platform")
include(":contexts:graph:api")
project(":contexts:graph:api").projectDir = file("contexts/graph/api")
include("contexts:graph:api:contexts-graph-api")
include("kernel:domain")
include("kernel:db")