package crow.evilo.plugin.catalog

import crow.evilo.model.MediaKind
import crow.evilo.plugin.api.PluginDescriptor

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
