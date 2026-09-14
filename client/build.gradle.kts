plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
}

description = "Janzeer SDK client: typed REST client, JSON-RPC over HTTP and WebSocket, finality helpers"

sourceSets {
    create("e2eTest") {
        kotlin.srcDir("src/e2eTest/kotlin")
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}
val e2eTestImplementation: Configuration by configurations.getting { extendsFrom(configurations.testImplementation.get()) }
val e2eTestRuntimeOnly: Configuration by configurations.getting { extendsFrom(configurations.testRuntimeOnly.get()) }

dependencies {
    api(project(":janzeer-sdk-core"))
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.mockwebserver)
}

/** The 14-step conformance flow against a running network: ./gradlew :janzeer-sdk-client:e2eTest -Djanzeer.node.url=… */
val e2eTest by tasks.registering(Test::class) {
    description = "Runs the e2e SPEC flow against JANZEER_NODE_URL / -Djanzeer.node.url"
    group = "verification"
    testClassesDirs = sourceSets["e2eTest"].output.classesDirs
    classpath = sourceSets["e2eTest"].runtimeClasspath
    useJUnitPlatform()
    for (k in listOf("janzeer.node.url", "janzeer.rpc.url", "janzeer.ws.url", "janzeer.e2e.mnemonic", "janzeer.e2e.recipient", "janzeer.e2e.networkId")) {
        val env = System.getenv(k.uppercase().replace('.', '_'))
        val prop = System.getProperty(k) ?: env
        if (prop != null) systemProperty(k, prop)
    }
    outputs.upToDateWhen { false }
}

mavenPublishing {
    coordinates("org.janzeer", "janzeer-sdk-client", version.toString())
    pom {
        name.set("janzeer-sdk-client")
        description.set(project.description)
        inceptionYear.set("2026")
    }
}
