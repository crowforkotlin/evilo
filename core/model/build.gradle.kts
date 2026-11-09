import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("evilo.multiplatform")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }

    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.serialization.core)
    }
}
