import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("evilo.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    android {
        androidResources {
            enable = true
        }
    }

    sourceSets.commonMain.dependencies {
        implementation(catalog.findLibrary("compose-runtime").get())
        implementation(catalog.findLibrary("compose-foundation").get())
        implementation(catalog.findLibrary("compose-material3").get())
        implementation(catalog.findLibrary("compose-ui").get())
        implementation(catalog.findLibrary("compose-components-resources").get())
    }
}
