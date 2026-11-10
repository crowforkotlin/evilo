package crow.evilo.plugin.catalog

import crow.evilo.model.MediaKind
import crow.evilo.plugin.api.HOST_STORAGE_CAPABILITY
import crow.evilo.plugin.api.PluginJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogParserTest {
    private val parser = CatalogParser()

    @Test
    fun catalogRoundTripsAndAllowsUnknownOptionalFields() {
        val catalog = pluginCatalog()
        val encoded = PluginJson.encodeToString(catalog)
        assertEquals(catalog, parser.parse(encoded, false))
        assertEquals(catalog, parser.parse("{\"futureMetadata\":true," + encoded.drop(1), false))
    }

    @Test
    fun unsupportedSchemaAndDuplicateIdentitiesAreRejected() {
        assertRejected(pluginCatalog().copy(schemaVersion = 2))
        assertRejected(pluginCatalog(listOf(catalogPlugin(), catalogPlugin())))
        val first = catalogRelease()
        assertRejected(pluginCatalog(listOf(catalogPlugin(listOf(first, first)))))
        assertRejected(pluginCatalog(listOf(catalogPlugin(listOf(first, first.copy(versionCode = 2))))))
    }

    @Test
    fun sourceLinksCannotPointToDecodedBinariesOrCredentialDownloads() {
        for (source in listOf(
            "https://code.example.com/plugin.wasm",
            "https://code.example.com/plugin.%77asm",
            "https://code.example.com/plugin.%2577asm",
            "https://code.example.com/plugin.wlm",
            "https://code.example.com/source?download=plugin.zip",
        )) assertRejected(pluginCatalog(listOf(catalogPlugin().copy(sourceUrl = source))))
        assertRejectedRelease(catalogRelease().copy(sourceArchiveUrl = "https://plugins.example.com/plugin.wasm"))
        assertRejectedRelease(catalogRelease().copy(sourceRevision = "main"))
        assertRejectedRelease(catalogRelease().copy(sourceArchiveSha256 = "not-a-hash"))
    }

    @Test
    fun manifestsMustBeDirectWlmLinks() {
        assertRejectedRelease(catalogRelease().copy(manifestUrl = "https://plugins.example.com/plugin.wasm"))
        assertRejectedRelease(catalogRelease().copy(manifestUrl = "https://plugins.example.com/manifest.wlm?token=test"))
    }

    @Test
    fun releasesMustMatchDeclaredKindsLanguagesAndPluginDates() {
        assertRejectedRelease(catalogRelease().copy(kinds = setOf(MediaKind.NOVEL)))
        assertRejectedRelease(catalogRelease().copy(languages = listOf("en", "en")))
        assertRejectedRelease(catalogRelease().copy(languages = listOf("zh")))
        assertRejectedRelease(catalogRelease().copy(publishedAt = "2026-11-10T00:00:00Z"))
        assertRejectedRelease(catalogRelease().copy(minimumHostVersionCode = 2, maximumHostVersionCode = 1))
    }

    @Test
    fun requiredStorageCapabilityNeedsStoragePermission() {
        val release = catalogRelease()
        assertRejectedRelease(release.copy(protocol = release.protocol.copy(requiredCapabilities = setOf(HOST_STORAGE_CAPABILITY))))
        val permitted = release.copy(protocol = release.protocol.copy(requiredCapabilities = setOf(HOST_STORAGE_CAPABILITY)),
            permissions = release.permissions.copy(storage = true))
        assertEquals(permitted, parser.validate(pluginCatalog(listOf(catalogPlugin(listOf(permitted)))), false).plugins.single().latestRelease)
    }

    @Test
    fun cleartextCatalogMetadataNeedsExplicitSubscriptionConsent() {
        val catalog = pluginCatalog(listOf(catalogPlugin().copy(iconUrl = "http://plugins.example.com/icon.png")))
        assertRejected(catalog)
        assertEquals(catalog, parser.validate(catalog, true))
    }

    private fun assertRejectedRelease(release: PluginRelease) = assertRejected(pluginCatalog(listOf(catalogPlugin(listOf(release)))))

    private fun assertRejected(catalog: PluginCatalog) {
        assertFailsWith<IllegalArgumentException> { parser.validate(catalog, false) }
    }
}
