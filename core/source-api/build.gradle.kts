import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("evilo.multiplatform")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.wasmline)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }

    sourceSets.commonMain.dependencies {
        api(projects.core.model)
        api(libs.wasmline)
    }
}
