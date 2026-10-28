import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.bcv)
}

allprojects {
    group = "org.janzeer"
    version = "0.1.3"
}

apiValidation { ignoredProjects += listOf("examples") }

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(17)
            compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
            if (project.name != "examples") explicitApi()
        }
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
            withSourcesJar()
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
        }
    }
}

dependencies {
    dokka(project(":janzeer-sdk-core"))
    dokka(project(":janzeer-sdk-client"))
}
dokka { moduleName.set("janzeer-sdk") }
