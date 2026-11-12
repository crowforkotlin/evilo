package crow.evilo.plugin.catalog

import crow.evilo.model.MediaKind
import crow.evilo.plugin.api.PluginDescriptor
import crow.evilo.plugin.api.PluginJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

internal fun catalogRelease(versionCode: Long = 1): PluginRelease = PluginRelease(
    version = "$versionCode.0.0",
    versionCode = versionCode,
    kinds = setOf(MediaKind.MANGA),
    languages = listOf("en"),
    manifestUrl = "https://plugins.example.com/example/$versionCode/manifest.wlm",
    sourceUrl = "https://code.example.com/example/tree/${"a".repeat(40)}",
    sourceRevision = "a".repeat(40),
    sourceArchiveUrl = "https://plugins.example.com/example/$versionCode/sources.zip",
    sourceArchiveSha256 = "b".repeat(64),
    publishedAt = "2026-11-09T00:00:00Z",
)

internal fun catalogPlugin(releases: List<PluginRelease> = listOf(catalogRelease())): CatalogPlugin = CatalogPlugin(
    id = "test.plugin",
    name = "Test plugin",
    authors = listOf(PluginAuthor("Test author")),
    overseas = false,
    languages = listOf("en"),
    kinds = setOf(MediaKind.MANGA),
    nsfw = false,
    description = "Offline catalog test fixture.",
    sourceUrl = "https://code.example.com/example",
    iconUrl = "https://plugins.example.com/example/icon.png",
    version = releases.maxBy { it.versionCode }.version,
    createdAt = "2026-11-08T00:00:00Z",
    updatedAt = "2026-11-09T00:00:00Z",
    availableVersions = releases,
)

internal fun pluginCatalog(plugins: List<CatalogPlugin> = listOf(catalogPlugin())): PluginCatalog = PluginCatalog(
    schemaVersion = PLUGIN_CATALOG_SCHEMA_VERSION,
    id = "test.catalog",
    name = "Test catalog",
    description = "Offline test fixture; not a hosted plugin source.",
    updatedAt = "2026-11-09T00:00:00Z",
    plugins = plugins,
)

internal fun CatalogPlugin.descriptor(release: PluginRelease) = PluginDescriptor(
    id, name, release.version, release.versionCode, release.kinds, release.languages, release.protocol, release.permissions,
)

internal fun compactCatalogJson(plugin: CatalogPlugin = catalogPlugin()): JsonObject {
    val fields = PluginJson.encodeToJsonElement(plugin).jsonObject - setOf(
        "overseas", "nsfw", "iconUrl", "version", "createdAt", "updatedAt", "forceUpdate", "minimumUsableVersionCode",
    )
    val releases = plugin.availableVersions.map { release ->
        JsonObject(PluginJson.encodeToJsonElement(release).jsonObject - setOf(
            "kinds", "languages", "sourceUrl", "permissions", "protocol", "usable", "minimumHostVersionCode", "maximumHostVersionCode",
        ))
    }
    val entry = JsonObject(fields + mapOf(
        "authors" to JsonArray(plugin.authors.map { author ->
            if (author.url == null) JsonPrimitive(author.name) else PluginJson.encodeToJsonElement(author)
        }),
        "availableVersions" to JsonArray(releases),
        "permissions" to PluginJson.encodeToJsonElement(plugin.latestRelease.permissions),
        "protocol" to PluginJson.encodeToJsonElement(plugin.latestRelease.protocol),
    ))
    return JsonObject(mapOf("name" to JsonPrimitive("Test catalog"), "plugins" to JsonArray(listOf(entry))))
}
