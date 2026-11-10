import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
kotlin { jvmToolchain(catalog.findVersion("jvm").get().requiredVersion.toInt()) }
