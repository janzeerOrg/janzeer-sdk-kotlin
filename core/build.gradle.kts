plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
}

description = "Janzeer SDK core: keys, addresses, transaction builders and signing (no networking)"

dependencies {
    implementation(libs.bcprov)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.serialization.json)
}

mavenPublishing {
    coordinates("org.janzeer", "janzeer-sdk-core", version.toString())
    pom {
        name.set("janzeer-sdk-core")
        description.set(project.description)
        inceptionYear.set("2026")
    }
}
