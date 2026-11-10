pluginManagement {
    includeBuild("evilo-build-logic")
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

rootProject.name = "evilo-root"
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

include(
    ":evilo-library",
    ":evilo-core",
    ":evilo-app",
    ":evilo-android",
    ":evilo-desktop",
    ":evilo-web",
)
