plugins {
    id("evilo.multiplatform")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.wasmline)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.eviloLibrary)
            api(libs.wasmline.loader)
            api(libs.ktor.client.core)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.okio)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.wasmline.engine.pulley)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.wasmline.engine.pulley)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.wasmline.engine.pulley)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
