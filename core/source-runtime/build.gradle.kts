plugins {
    id("evilo.multiplatform")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.wasmline)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.core.sourceApi)
        api(libs.wasmline.loader)
        implementation(libs.kotlinx.coroutines.core)
    }
}
