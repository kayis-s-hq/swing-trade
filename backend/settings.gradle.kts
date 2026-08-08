rootProject.name = "swing-trade"

include("core")
include("data")
include("strategy")
include("llm")
include("broker")
include("api")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
    }
}
