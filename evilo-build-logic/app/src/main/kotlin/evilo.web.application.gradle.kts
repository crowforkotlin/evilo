import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    js {
        browser { commonWebpackConfig { outputFileName = "app.js" } }
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "app.js" } }
        binaries.executable()
    }
    applyDefaultHierarchyTemplate()
    sourceSets.commonMain.dependencies {
        implementation(catalog.findLibrary("compose-ui").get())
    }
}
