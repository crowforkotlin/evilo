import crow.evilo.buildlogic.eviloProperty
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins { id("evilo.desktop.application") }

dependencies {
    implementation(projects.eviloApp)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
}

// Development runs show diagnostics; packaged applications do not inherit this property.
tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    systemProperty("evilo.debugDiagnostics", "true")
}

compose.desktop {
    application {
        mainClass = "crow.evilo.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = eviloProperty("EVILO_APPLICATION_ID")
            packageVersion = eviloProperty("EVILO_VERSION_NAME")
        }
    }
}
