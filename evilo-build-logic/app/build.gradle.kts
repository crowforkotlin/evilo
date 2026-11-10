plugins { `kotlin-dsl` }

kotlin { jvmToolchain(libs.versions.jvm.get().toInt()) }

dependencies {
    implementation(project(":base"))
    implementation(libs.gradle.android)
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.compose)
    implementation(libs.gradle.composeCompiler)
}
