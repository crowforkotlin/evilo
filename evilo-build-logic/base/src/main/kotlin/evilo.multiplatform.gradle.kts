import crow.evilo.buildlogic.eviloProperty
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

// Kotlin DSL generates convention accessors in a project without the consumer's catalogs.
val catalog = extensions.getByType<VersionCatalogsExtension>().find("libs").orElse(null)

if (catalog != null) {
    val jvmVersion = catalog.findVersion("jvm").get().requiredVersion
    val modulePackage = project.name.removePrefix("evilo-").replace('-', '.')

    kotlin {
        jvmToolchain(jvmVersion.toInt())

        android {
            namespace = "${eviloProperty("EVILO_NAMESPACE")}.$modulePackage"
            compileSdk = catalog.findVersion("android-compileSdk").get().requiredVersion.toInt()
            minSdk = catalog.findVersion("android-minSdk").get().requiredVersion.toInt()
            compilerOptions {
                jvmTarget = JvmTarget.fromTarget(jvmVersion)
            }
            withHostTest {}
        }

        jvm {
            compilerOptions {
                jvmTarget = JvmTarget.fromTarget(jvmVersion)
            }
        }
        iosArm64()
        iosSimulatorArm64()
        js {
            browser()
        }
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            browser()
        }

        applyDefaultHierarchyTemplate()

        sourceSets.commonTest.dependencies {
            implementation(catalog.findLibrary("kotlin-test").get())
        }
    }
}
