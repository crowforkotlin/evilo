package crow.evilo.plugin.catalog

import crow.evilo.plugin.api.PluginJson
import crow.evilo.network.ktor.PluginUrlPolicy
import crow.evilo.network.ktor.validatePluginPermissions
import io.ktor.http.decodeURLPart
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

class CatalogParser {
    fun parse(text: String, allowHttp: Boolean): PluginCatalog {
        require(text.encodeToByteArray().size <= 2 * 1024 * 1024) { "Plugin catalog exceeds 2 MiB." }
        val json = normalizeCatalogJson(PluginJson.parseToJsonElement(text).jsonObject)
        return validate(PluginJson.decodeFromJsonElement<PluginCatalog>(json), allowHttp)
    }

    fun validate(catalog: PluginCatalog, allowHttp: Boolean): PluginCatalog {
        require(catalog.schemaVersion == PLUGIN_CATALOG_SCHEMA_VERSION) { "Unsupported plugin catalog format version ${catalog.schemaVersion}." }
        require(catalog.id.length <= 128 && ID.matches(catalog.id)) { "Invalid catalog ID." }
        requireText(catalog.name, 120, "catalog name")
        requireText(catalog.description, 4096, "catalog description", allowEmpty = true)
        Instant.parse(catalog.updatedAt)
        require(catalog.plugins.size <= 512) { "A catalog may contain at most 512 plugins." }
        require(catalog.plugins.distinctBy { it.id }.size == catalog.plugins.size) { "Duplicate plugin IDs in catalog." }
        catalog.plugins.forEach { plugin -> validatePlugin(plugin, allowHttp) }
        return catalog
    }

    private fun validatePlugin(plugin: CatalogPlugin, allowHttp: Boolean) {
        require(plugin.id.length <= 128 && ID.matches(plugin.id)) { "Invalid plugin ID." }
        requireText(plugin.name, 120, "plugin name")
        requireText(plugin.description, 8192, "plugin description")
        require(plugin.authors.size in 1..16) { "A plugin must identify its authors." }
        plugin.authors.forEach {
            requireText(it.name, 120, "author name")
            it.url?.let { url -> PluginUrlPolicy.check(url, allowHttp) }
        }
        require(plugin.languages.size in 1..32 && plugin.languages.distinct().size == plugin.languages.size && plugin.languages.all(LANGUAGE::matches)) { "Invalid plugin languages." }
        require(plugin.kinds.isNotEmpty()) { "A plugin must declare its media kinds." }
        sourceUrl(plugin.sourceUrl, allowHttp)
        if (plugin.iconUrl.isNotEmpty()) PluginUrlPolicy.check(plugin.iconUrl, allowHttp)
        val created = Instant.parse(plugin.createdAt)
        val updated = Instant.parse(plugin.updatedAt)
        require(updated >= created) { "Plugin update date precedes its creation date." }
        require(plugin.availableVersions.size in 1..64) { "A plugin must list 1 to 64 available versions." }
        require(plugin.availableVersions.distinctBy { it.versionCode }.size == plugin.availableVersions.size &&
            plugin.availableVersions.distinctBy { it.version }.size == plugin.availableVersions.size) { "Duplicate plugin versions." }
        plugin.availableVersions.forEach { release ->
            require(release.version.length <= 64 && VERSION.matches(release.version) && release.versionCode > 0) { "Invalid plugin version." }
            require(release.kinds.isNotEmpty() && plugin.kinds.containsAll(release.kinds)) { "Release media kinds must be declared by the plugin." }
            require(release.languages.isNotEmpty() && release.languages.size <= 32 &&
                release.languages.distinct().size == release.languages.size && release.languages.all(LANGUAGE::matches) &&
                plugin.languages.containsAll(release.languages)) { "Release languages must be declared by the plugin." }
            val manifest = PluginUrlPolicy.check(release.manifestUrl, allowHttp)
            require(manifest.encodedPath.endsWith(".wlm") && manifest.encodedQuery.isEmpty()) { "Plugin releases require a direct .wlm URL." }
            sourceUrl(release.sourceUrl, allowHttp)
            require(REVISION.matches(release.sourceRevision)) { "A release must identify an immutable source commit." }
            val archive = PluginUrlPolicy.check(release.sourceArchiveUrl, allowHttp)
            require(archive.encodedQuery.isEmpty() && decodedPath(archive.encodedPath).let { it.endsWith(".zip") || it.endsWith(".tar.gz") }) {
                "A release must provide a source ZIP or tar.gz archive, not just Wasm binaries."
            }
            require(Regex("[0-9a-f]{64}").matches(release.sourceArchiveSha256)) { "A release must provide its source archive SHA-256." }
            release.manifestSha256?.let { require(Regex("[0-9a-f]{64}").matches(it)) { "Invalid manifest SHA-256." } }
            release.signing?.let { signing ->
                require(signing.algorithm == "Ed25519" && signing.keyId.length in 1..128 &&
                    Regex("[A-Za-z0-9_.-]+").matches(signing.keyId) &&
                    Regex("[0-9a-f]{64}").matches(signing.publicKeyHex)) { "Invalid publisher signing identity." }
                require(release.manifestSha256 != null) { "Publisher-signed releases must pin their manifest SHA-256." }
            }
            require(!release.protocol.requiredCapabilities.contains("storage.kv.v1") || release.permissions.storage) {
                "Required storage capability must have a matching storage permission."
            }
            require(Instant.parse(release.publishedAt) in created..updated) { "Release publication date is outside plugin dates." }
            require(release.minimumHostVersionCode > 0 &&
                (release.maximumHostVersionCode == null || release.maximumHostVersionCode >= release.minimumHostVersionCode)) {
                "Invalid host version range."
            }
            require(release.protocol.major > 0 && release.protocol.minimumMinor >= 0) { "Invalid protocol compatibility." }
            require((release.protocol.requiredCapabilities + release.protocol.optionalCapabilities).all(CAPABILITY::matches)) {
                "Invalid capability identifiers."
            }
            validatePluginPermissions(release.permissions, allowHttp)
        }
        require(plugin.version == plugin.latestRelease.version) { "Plugin version must match its newest listed release." }
        require(plugin.minimumUsableVersionCode in 1..plugin.latestRelease.versionCode) { "Invalid minimum usable plugin version." }
    }

    private fun sourceUrl(value: String, allowHttp: Boolean) {
        val url = PluginUrlPolicy.check(value, allowHttp)
        require(url.encodedQuery.isEmpty()) { "Source-code links must not use query-based downloads or credentials." }
        val path = decodedPath(url.encodedPath)
        require(listOf(".wlm", ".wasm", ".pwasm", ".cwasm").none(path::endsWith)) {
            "Plugin source links must point to source code, not a manifest or binary."
        }
    }

    private fun decodedPath(path: String): String {
        var decoded = path
        repeat(3) { decoded = decoded.decodeURLPart() }
        return decoded.lowercase()
    }

    private fun requireText(value: String, limit: Int, name: String, allowEmpty: Boolean = false) {
        require(value.length <= limit && (allowEmpty || value.isNotBlank()) && value.none { it == '\u0000' }) { "Invalid $name." }
    }

    private companion object {
        val ID = Regex("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*")
        val LANGUAGE = Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*")
        val VERSION = Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?")
        val REVISION = Regex("[0-9a-f]{40}(?:[0-9a-f]{24})?")
        val CAPABILITY = Regex("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+")
    }
}
