import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("evilo.multiplatform")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.wasmline)
    `maven-publish`
}

group = "io.github.crowforkotlin.evilo"
version = libs.versions.evilo.plugin.sdk.get()

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }

    sourceSets.commonMain.dependencies {
        api(libs.wasmline)
        api(libs.kotlinx.serialization.core)
        api(libs.kotlinx.serialization.json)
    }
}
