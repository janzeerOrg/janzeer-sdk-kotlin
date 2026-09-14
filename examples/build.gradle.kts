plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":janzeer-sdk-client"))
    implementation(libs.coroutines.core)
}

application { mainClass.set(project.findProperty("mainClass")?.toString() ?: "org.janzeer.examples.QuickstartTransferKt") }
