plugins {
    id("evilo.multiplatform")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
    alias(libs.plugins.wasmline)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.eviloLibrary)
            api(libs.wasmline.loader)
            api(libs.ktor.client.core)
            api(libs.kotlinx.coroutines.core)
            api(libs.androidx.datastore.core)
            api(libs.androidx.room3.runtime)
            api(libs.koin.core)
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

dependencies {
    listOf("Android", "Jvm", "IosArm64", "IosSimulatorArm64", "Js", "WasmJs").forEach { target ->
        add("ksp$target", libs.androidx.room3.compiler)
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}
