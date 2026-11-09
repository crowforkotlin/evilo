pluginManagement {
    includeBuild("build-logic")
    providers.gradleProperty("wasmline.localPath").orNull?.let { includeBuild(it) }

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version
            providers.gradleProperty("foojayResolverVersion").get()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "evilo"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

providers.gradleProperty("wasmline.localPath").orNull?.let { includeBuild(it) }

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":app", ":app:android")
include(":core:model", ":core:source-api", ":core:source-runtime")
include(":feature:library")
