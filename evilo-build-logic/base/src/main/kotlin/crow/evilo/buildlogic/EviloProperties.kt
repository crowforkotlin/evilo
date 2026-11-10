package crow.evilo.buildlogic

import java.util.Properties
import org.gradle.api.Project

fun Project.eviloProperty(name: String): String = providers
    .fileContents(rootProject.layout.projectDirectory.file("project.properties"))
    .asText
    .map { content ->
        val properties = Properties().apply { content.reader().use(::load) }
        requireNotNull(properties.getProperty(name)) {
            "Missing $name in project.properties"
        }
    }
    .get()
