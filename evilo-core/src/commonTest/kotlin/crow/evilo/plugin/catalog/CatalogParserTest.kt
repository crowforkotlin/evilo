package crow.evilo.plugin.catalog

import crow.evilo.model.MediaKind
import crow.evilo.plugin.api.HOST_HTTP_CAPABILITY
import crow.evilo.plugin.api.HOST_MEDIA_CAPABILITY
import crow.evilo.plugin.api.HOST_STORAGE_CAPABILITY
import crow.evilo.plugin.api.HttpAccess
import crow.evilo.plugin.api.PluginJson
import crow.evilo.plugin.api.PluginPermissions
import crow.evilo.plugin.api.ProtocolRequirement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogParserTest {
    private val parser = CatalogParser()

    @Test
    fun emptyCatalogNeedsOnlyItsNameAndPluginList() {
        val catalog = parser.parse("""{"name":"My plugins","plugins":[]}""", false)
        assertEquals(PluginCatalog(PLUGIN_CATALOG_SCHEMA_VERSION, "catalog", "My plugins", "",
            "1970-01-01T00:00:00Z", emptyList()), catalog)
        assertEquals(catalog, parser.parse(PluginJson.encodeToString(catalog), false))
    }

    @Test
    fun compactCatalogInheritsCommonFieldsAndAcceptsStringAuthorsWithoutAnIcon() {
        val release = catalogRelease().copy(
            protocol = ProtocolRequirement(requiredCapabilities = setOf(HOST_MEDIA_CAPABILITY, HOST_HTTP_CAPABILITY, HOST_STORAGE_CAPABILITY)),
            permissions = PluginPermissions(api = HttpAccess(origins = setOf("https://api.example.com")),
                media = HttpAccess(origins = setOf("https://images.example.com")), storage = true),
        )
        val plugin = catalogPlugin(listOf(release))
        val catalog = parser.parse(compactCatalogJson(plugin).toString(), false)
        assertEquals(plugin.copy(iconUrl = "", createdAt = release.publishedAt,
            availableVersions = listOf(release.copy(sourceUrl = plugin.sourceUrl))), catalog.plugins.single())
        assertEquals(release.publishedAt, catalog.updatedAt)
    }

    @Test
    fun datesAndCurrentVersionAreDerivedIndependentlyOfReleaseListOrder() {
        val releases = listOf(
            catalogRelease(3).copy(publishedAt = "2026-11-09"),
            catalogRelease(1).copy(publishedAt = "2026-11-08"),
            catalogRelease(2).copy(publishedAt = "2026-11-10"),
        )
        val catalog = parser.parse(compactCatalogJson(catalogPlugin(releases)).toString(), false)
        val plugin = catalog.plugins.single()
        assertEquals("3.0.0", plugin.version)
        assertEquals("2026-11-08T00:00:00Z", plugin.createdAt)
        assertEquals("2026-11-10T00:00:00Z", plugin.updatedAt)
        assertEquals(plugin.updatedAt, catalog.updatedAt)
        assertEquals(listOf("2026-11-09T00:00:00Z", "2026-11-08T00:00:00Z", "2026-11-10T00:00:00Z"),
            plugin.availableVersions.map { it.publishedAt })
    }

    @Test
    fun explicitCatalogAndPluginDatesAcceptOrdinaryDates() {
        val input = compactCatalogJson().editPlugin { plugin ->
            JsonObject(plugin + mapOf("createdAt" to JsonPrimitive("2026-11-08"), "updatedAt" to JsonPrimitive("2026-11-10")))
        }
        val catalog = parser.parse(JsonObject(input + ("updatedAt" to JsonPrimitive("2026-11-11"))).toString(), false)
        assertEquals("2026-11-11T00:00:00Z", catalog.updatedAt)
        assertEquals("2026-11-08T00:00:00Z", catalog.plugins.single().createdAt)
        assertEquals("2026-11-10T00:00:00Z", catalog.plugins.single().updatedAt)
    }

    @Test
    fun explicitReleaseOverridesReplaceCommonFieldsWithoutMergingThem() {
        val first = catalogRelease().copy(permissions = PluginPermissions(api = HttpAccess(origins = setOf("https://old.example.com"))))
        val second = catalogRelease(2).copy(kinds = setOf(MediaKind.MANGA, MediaKind.NOVEL), languages = listOf("en", "zh"),
            protocol = ProtocolRequirement(requiredCapabilities = setOf(HOST_MEDIA_CAPABILITY, HOST_HTTP_CAPABILITY, HOST_STORAGE_CAPABILITY)),
            permissions = PluginPermissions(api = HttpAccess(origins = setOf("https://new.example.com")),
                media = HttpAccess(origins = setOf("https://images.example.com")), storage = true))
        val plugin = catalogPlugin(listOf(first, second)).copy(kinds = second.kinds, languages = second.languages)
        val input = compactCatalogJson(plugin).editFirstRelease { release ->
            JsonObject(release + mapOf(
                "kinds" to PluginJson.encodeToJsonElement(first.kinds),
                "languages" to PluginJson.encodeToJsonElement(first.languages),
                "sourceUrl" to JsonPrimitive(first.sourceUrl),
                "protocol" to JsonObject(mapOf("major" to JsonPrimitive(1))),
                "permissions" to JsonObject(mapOf("api" to JsonObject(mapOf(
                    "origins" to PluginJson.encodeToJsonElement(first.permissions.api.origins),
                )))),
            ))
        }
        val parsed = parser.parse(input.toString(), false).plugins.single()
        assertEquals(first, parsed.availableVersions.first())
        assertEquals(second.copy(sourceUrl = plugin.sourceUrl), parsed.availableVersions.last())
    }

    @Test
    fun missingIdentityAndReleaseProvenanceAreNotDefaulted() {
        val input = compactCatalogJson()
        for (field in listOf("name", "plugins")) assertJsonRejected(JsonObject(input - field))
        for (field in listOf("id", "name", "authors", "description", "kinds", "languages", "sourceUrl", "availableVersions")) {
            assertJsonRejected(input.editPlugin { JsonObject(it - field) })
        }
        for (field in listOf("version", "versionCode", "manifestUrl", "sourceRevision", "sourceArchiveUrl", "sourceArchiveSha256", "publishedAt")) {
            assertJsonRejected(input.editFirstRelease { JsonObject(it - field) })
        }
    }

    @Test
    fun invalidExplicitValuesAreNotReplacedByDefaults() {
        val input = compactCatalogJson()
        assertJsonRejected(JsonObject(input + ("schemaVersion" to JsonNull)))
        for ((field, value) in listOf("authors" to JsonArray(listOf(JsonPrimitive(""))), "overseas" to JsonNull,
            "iconUrl" to JsonNull, "version" to JsonPrimitive("9.0.0"), "updatedAt" to JsonPrimitive("2026-02-30"))) {
            assertJsonRejected(input.editPlugin { JsonObject(it + (field to value)) })
        }
        for ((field, value) in listOf("kinds" to JsonArray(emptyList()), "languages" to JsonArray(emptyList()),
            "sourceUrl" to JsonPrimitive("https://code.example.com/plugin.wasm"), "permissions" to JsonPrimitive("invalid"),
            "protocol" to JsonNull, "publishedAt" to JsonPrimitive("2026-02-30"),
            "manifestUrl" to JsonPrimitive("https://plugins.example.com/plugin.wasm"),
            "sourceRevision" to JsonPrimitive("main"), "sourceArchiveUrl" to JsonPrimitive("https://plugins.example.com/plugin.wasm"),
            "sourceArchiveSha256" to JsonPrimitive("not-a-hash"))) {
            assertJsonRejected(input.editFirstRelease { JsonObject(it + (field to value)) })
        }
        for (code in listOf(JsonPrimitive(0), JsonPrimitive(-1), JsonPrimitive(1.5), JsonPrimitive("invalid"))) {
            assertJsonRejected(input.editFirstRelease { JsonObject(it + ("versionCode" to code)) })
        }
    }

    @Test
    fun compactCatalogsStillCheckDeclaredIconUrlsAndCleartextConsent() {
        val input = compactCatalogJson().editPlugin { JsonObject(it + ("iconUrl" to JsonPrimitive("http://plugins.example.com/icon.png"))) }
        assertJsonRejected(input)
        assertEquals("http://plugins.example.com/icon.png", parser.parse(input.toString(), true).plugins.single().iconUrl)
        assertJsonRejected(compactCatalogJson().editPlugin { JsonObject(it + ("iconUrl" to JsonPrimitive("file:///tmp/icon.png"))) })
    }

    @Test
    fun fullTimestampRepresentationsRemainUnchanged() {
        val release = catalogRelease().copy(publishedAt = "2026-11-09T08:00:00+08:00")
        val plugin = catalogPlugin(listOf(release)).copy(createdAt = "2026-11-08T08:00:00+08:00", updatedAt = release.publishedAt)
        val catalog = pluginCatalog(listOf(plugin)).copy(updatedAt = release.publishedAt)
        assertEquals(catalog, parser.parse(PluginJson.encodeToString(catalog), false))
    }

    @Test
    fun catalogRoundTripsAndAllowsUnknownOptionalFields() {
        val catalog = pluginCatalog()
        val encoded = PluginJson.encodeToString(catalog)
        assertEquals(catalog, parser.parse(encoded, false))
        assertEquals(catalog, parser.parse("{\"futureMetadata\":true," + encoded.drop(1), false))
        assertEquals(catalog, parser.parse("{\"\$schema\":\"./catalog.schema.json\"," + encoded.drop(1), false))
    }

    @Test
    fun removedCatalogMetadataIsIgnoredInExistingJson() {
        val catalog = pluginCatalog()
        val json = PluginJson.parseToJsonElement(PluginJson.encodeToString(catalog)).jsonObject
        val plugin = json.getValue("plugins").jsonArray.single().jsonObject
        val legacyPlugin = JsonObject(plugin + ("certified" to JsonPrimitive(true)))
        val legacyCatalog = JsonObject(json + ("plugins" to JsonArray(listOf(legacyPlugin))))
        assertEquals(catalog, parser.parse(legacyCatalog.toString(), false))
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

    private fun assertJsonRejected(catalog: JsonObject) {
        assertFailsWith<IllegalArgumentException> { parser.parse(catalog.toString(), false) }
    }

    private fun JsonObject.editPlugin(edit: (JsonObject) -> JsonObject): JsonObject =
        JsonObject(this + ("plugins" to JsonArray(listOf(edit(getValue("plugins").jsonArray.single().jsonObject)))))

    private fun JsonObject.editFirstRelease(edit: (JsonObject) -> JsonObject): JsonObject = editPlugin { plugin ->
        val releases = plugin.getValue("availableVersions").jsonArray
        JsonObject(plugin + ("availableVersions" to JsonArray(listOf(edit(releases.first().jsonObject)) + releases.drop(1))))
    }
}
