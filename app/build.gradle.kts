import buildlogic.eviloProperty
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("evilo.compose")
    alias(libs.plugins.wasmline)
}

kotlin {
    android {
        namespace = "${eviloProperty("EVILO_NAMESPACE")}.app.shared"
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Evilo"
            isStatic = true
        }
    }

    js {
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.library)
            implementation(projects.core.sourceRuntime)
        }
        androidMain.dependencies {
            implementation(libs.wasmline.engine.pulley)
        }
        iosMain.dependencies {
            implementation(libs.wasmline.engine.pulley)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.wasmline.engine.pulley)
        }
    }
}

compose.resources {
    packageOfResClass = "crow.evilo.app.resources"
}

compose.desktop {
    application {
        mainClass = "crow.evilo.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = eviloProperty("EVILO_APPLICATION_ID")
            packageVersion = eviloProperty("EVILO_VERSION_NAME")
        }
    }
}
