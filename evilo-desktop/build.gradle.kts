import crow.evilo.buildlogic.eviloProperty
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins { id("evilo.desktop.application") }

dependencies {
    implementation(projects.eviloApp)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
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
