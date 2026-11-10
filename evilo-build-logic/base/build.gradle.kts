plugins { `kotlin-dsl` }

kotlin { jvmToolchain(libs.versions.jvm.get().toInt()) }

dependencies {
    implementation(libs.gradle.android)
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.composeCompiler)
}
