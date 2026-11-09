plugins {
    id("evilo.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.core.model)
    }
}

compose.resources {
    packageOfResClass = "crow.evilo.feature.library.resources"
}
