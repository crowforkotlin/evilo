package crow.evilo.plugin.catalog

import crow.evilo.model.MediaKind
import crow.evilo.plugin.api.PluginPermissions
import crow.evilo.plugin.api.ProtocolRequirement
import kotlinx.serialization.Serializable
import kotlin.time.Instant

const val PLUGIN_CATALOG_SCHEMA_VERSION = 1
const val BUNDLED_SOURCE_ID = "bundled"
const val DEFAULT_BUNDLED_CATALOG_URL =
    "https://raw.githubusercontent.com/crowforkotlin/evilo/main/plugin-repository/plugin.json"

@Serializable
data class PluginAuthor(val name: String, val url: String? = null)

@Serializable
data class PluginSigningIdentity(
    val keyId: String,
    val publicKeyHex: String,
    val algorithm: String = "Ed25519",
)

@Serializable
data class PluginRelease(
    val version: String,
    val versionCode: Long,
    val kinds: Set<MediaKind>,
    val languages: List<String>,
    val manifestUrl: String,
    val sourceUrl: String,
    val sourceRevision: String,
    val sourceArchiveUrl: String,
    val sourceArchiveSha256: String,
    val publishedAt: String,
    val usable: Boolean = true,
    val minimumHostVersionCode: Long = 1,
    val maximumHostVersionCode: Long? = null,
    val protocol: ProtocolRequirement = ProtocolRequirement(),
    val permissions: PluginPermissions = PluginPermissions(),
    val signing: PluginSigningIdentity? = null,
    val manifestSha256: String? = null,
)

@Serializable
data class CatalogPlugin(
    val id: String,
    val name: String,
    val authors: List<PluginAuthor>,
    val overseas: Boolean,
    val languages: List<String>,
    val kinds: Set<MediaKind>,
    val nsfw: Boolean,
    val description: String,
    val sourceUrl: String,
    val iconUrl: String,
    val version: String,
    val createdAt: String,
    val updatedAt: String,
    val forceUpdate: Boolean = false,
    val minimumUsableVersionCode: Long = 1,
    val availableVersions: List<PluginRelease>,
) {
    val latestRelease: PluginRelease get() = availableVersions.maxBy { it.versionCode }
}

@Serializable
data class PluginCatalog(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val description: String,
    val updatedAt: String,
    val plugins: List<CatalogPlugin>,
)

@Serializable
data class CatalogSubscription(
    val id: String,
    val url: String,
    val allowHttp: Boolean = false,
    val enabled: Boolean = true,
)

data class CatalogSnapshot(
    val subscription: CatalogSubscription,
    val catalog: PluginCatalog,
    val error: String? = null,
    val fromCache: Boolean = false,
)

data class CatalogListing(val snapshot: CatalogSnapshot, val plugin: CatalogPlugin) {
    val key: String get() = "${snapshot.subscription.id}:${plugin.id}"
}

@Serializable
data class InstalledPlugin(
    val sourceId: String,
    val sourceUrl: String,
    val plugin: CatalogPlugin,
    val release: PluginRelease,
    val installedAt: String,
    val allowHttp: Boolean,
    val approvedManifestSha256: String? = null,
) {
    val key: String get() = "$sourceId:${plugin.id}"
}

internal fun installationOrderTime(value: String): Instant? = try { Instant.parse(value) } catch (_: IllegalArgumentException) { null }
