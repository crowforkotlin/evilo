import crow.evilo.buildlogic.eviloProperty
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("evilo.android.application")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(libs.versions.jvm.get())
    }
}
dependencies {
    implementation(projects.eviloApp)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// Release signing credentials stay outside the repository: a machine that has them sets
// `evilo.releaseStoreFile` and friends, typically in ~/.gradle/gradle.properties.
val releaseStore = providers.gradleProperty("evilo.releaseStoreFile").orNull

android {
    namespace = eviloProperty("EVILO_APPLICATION_ID")
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = eviloProperty("EVILO_APPLICATION_ID")
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = eviloProperty("EVILO_VERSION_CODE").toInt()
        versionName = eviloProperty("EVILO_VERSION_NAME")
        resValue("string", "app_name", eviloProperty("EVILO_NAME"))
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = rootProject.file(releaseStore)
                storePassword = providers.gradleProperty("evilo.releaseStorePassword").orNull
                keyAlias = providers.gradleProperty("evilo.releaseKeyAlias").orNull
                keyPassword = providers.gradleProperty("evilo.releaseKeyPassword").orNull
            }
        }
    }
    buildTypes {
        release {
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
    }
    buildFeatures {
        compose = true
        resValues = true
    }
}
