import crow.evilo.buildlogic.GenerateEviloAppConfiguration
import crow.evilo.buildlogic.GenerateEviloPluginKeys
import crow.evilo.buildlogic.eviloPluginKeyDirectory
import crow.evilo.buildlogic.eviloProperty
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("evilo.compose")
    alias(libs.plugins.wasmline)
}

val pluginKeys = tasks.register<GenerateEviloPluginKeys>("generatePluginSigningKeys") {
    group = "evilo"
    description = "Create the unique plugin-signing authority outside the repository; never overwrite existing keys."
    keyDirectory.set(eviloPluginKeyDirectory())
    repositoryDirectory.set(rootProject.layout.projectDirectory)
    outputs.upToDateWhen { false }
}
val appConfiguration = tasks.register<GenerateEviloAppConfiguration>("generateEviloAppConfiguration") {
    val supplied = providers.environmentVariable("EVILO_PUBLIC_KEY_HEX")
    publicKeyHex.set(supplied)
    if (!supplied.isPresent) publicKeyFile.set(layout.file(provider { eviloPluginKeyDirectory().resolve("public.key") }))
    appVersion.set(eviloProperty("EVILO_VERSION_NAME"))
    appVersionCode.set(eviloProperty("EVILO_VERSION_CODE").toLong())
    outputDirectory.set(layout.buildDirectory.dir("generated/eviloConfiguration"))
}
tasks.withType<KotlinCompilationTask<*>>().configureEach { dependsOn(appConfiguration) }

kotlin {
    sourceSets.commonMain.get().kotlin.srcDir(appConfiguration.flatMap { it.outputDirectory })
    android {
        namespace = "${eviloProperty("EVILO_NAMESPACE")}.app.shared"
    }

    // Browser UI test bundles need the Skiko executable runtime even in this shared module.
    js { binaries.executable() }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { binaries.executable() }

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Evilo"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.eviloCore)
            implementation(libs.compose.animation)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.coil.compose)
            implementation(libs.coil.core)
            implementation(libs.koin.compose)
            implementation(libs.androidx.datastore.okio)
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        jvmMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        jvmTest.dependencies {
            // Dock/cover geometry tests build real Skia paths, which need the host runtime.
            implementation(compose.desktop.currentOs)
        }
        iosMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        webMain.dependencies {
            implementation(libs.androidx.sqlite.web)
            implementation(libs.kotlinx.browser)
        }
    }
}

compose.resources {
    packageOfResClass = "crow.evilo.app.resources"
}
