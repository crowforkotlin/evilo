plugins { id("evilo.web.application") }

kotlin {
    sourceSets.commonMain.dependencies { implementation(projects.eviloApp) }
}
