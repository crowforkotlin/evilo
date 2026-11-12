package crow.evilo.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateBundledPluginCatalog : DefaultTask() {
    @get:InputFile abstract val catalogFile: RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val text = catalogFile.get().asFile.readText()
        require(text.encodeToByteArray().size <= 2 * 1024 * 1024) { "Bundled plugin catalog exceeds 2 MiB." }
        // JVM string constants have a 64 KiB ceiling; escaped chunks also preserve split surrogate pairs.
        val literals = text.chunked(12_000).joinToString(",\n") { chunk ->
            "    \"" + buildString {
                chunk.forEach { character ->
                    when (character) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '$' -> append("\\$")
                        else -> if (character.code in 32..126) append(character)
                            else append("\\u" + character.code.toString(16).padStart(4, '0'))
                    }
                }
            } + "\""
        }
        val output = outputDirectory.file("crow/evilo/feature/browse/BundledPluginCatalog.kt").get().asFile
        output.parentFile.mkdirs()
        output.writeText("package crow.evilo.feature.browse\n\nval BundledPluginCatalogJson: String = listOf(\n$literals\n).joinToString(\"\")\n")
    }
}
