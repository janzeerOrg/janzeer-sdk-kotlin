pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral() }
}
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0" }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "janzeer-sdk"
include("core", "client", "examples")
project(":core").name = "janzeer-sdk-core"
project(":client").name = "janzeer-sdk-client"
