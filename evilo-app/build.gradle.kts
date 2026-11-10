import crow.evilo.buildlogic.GenerateBundledPluginCatalog
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

val bundledCatalog = tasks.register<GenerateBundledPluginCatalog>("generateBundledPluginCatalog") {
    catalogFile.set(rootProject.layout.projectDirectory.file("plugin-repository/official.json"))
    outputDirectory.set(layout.buildDirectory.dir("generated/officialCatalog"))
}
val pluginKeys = tasks.register<GenerateEviloPluginKeys>("generatePluginSigningKeys") {
    group = "evilo"
    description = "Create the unique plugin-signing authority outside the repository; never overwrite existing keys."
    keyDirectory.set(eviloPluginKeyDirectory())
    repositoryDirectory.set(rootProject.layout.projectDirectory)
    outputs.upToDateWhen { false }
}
val appConfiguration = tasks.register<GenerateEviloAppConfiguration>("generateEviloAppConfiguration") {
    publicKeyFile.set(pluginKeys.flatMap { it.keyDirectory.file("public.key") })
    appVersion.set(eviloProperty("EVILO_VERSION_NAME"))
    appVersionCode.set(eviloProperty("EVILO_VERSION_CODE").toLong())
    outputDirectory.set(layout.buildDirectory.dir("generated/eviloConfiguration"))
}
tasks.withType<KotlinCompilationTask<*>>().configureEach { dependsOn(appConfiguration, bundledCatalog) }

kotlin {
    sourceSets.commonMain.get().kotlin.srcDir(appConfiguration.flatMap { it.outputDirectory })
    sourceSets.commonMain.get().kotlin.srcDir(bundledCatalog.flatMap { it.outputDirectory })
    android {
        namespace = "${eviloProperty("EVILO_NAMESPACE")}.app.shared"
    }

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
            implementation(libs.okio)
        }
    }
}

compose.resources {
    packageOfResClass = "crow.evilo.app.resources"
}
