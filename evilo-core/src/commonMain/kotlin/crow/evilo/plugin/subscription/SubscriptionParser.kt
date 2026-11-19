package crow.evilo.plugin.subscription

import crow.evilo.network.ktor.PluginUrlPolicy
import crow.evilo.plugin.catalog.CatalogParser
import crow.evilo.plugin.catalog.normalizePluginJson
import crow.evilo.plugin.catalog.CatalogPlugin
import crow.evilo.plugin.catalog.PLUGIN_CATALOG_SCHEMA_VERSION
import crow.evilo.plugin.catalog.PluginCatalog
import crow.evilo.plugin.api.PluginJson
import crow.evilo.plugin.subscription.crypto.Ed25519
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

/** The pointer a collection publishes: where the index lives and who signed it. */
@Serializable
data class SubscriptionPointer(
    val name: String,
    val indexUrl: String,
    val signingKeyFingerprint: String,
    val keyId: String,
)

/** The signed index: every plugin of every listed repository, merged once. */
@Serializable
data class SubscriptionIndex(
    val schemaVersion: Int,
    val name: String,
    val updatedAt: String,
    val plugins: List<IndexedPlugin>,
)

/**
 * One plugin as the index records it: where its own catalog lives, plus its newest
 * release only. Carrying that release is what makes an index source installable
 * without fetching every catalog first; older versions remain in the plugin's catalog.
 */
@Serializable
data class IndexedPlugin(
    val catalogUrl: String,
    val plugin: CatalogPlugin,
)

/** What an address actually served. */
sealed interface SubscriptionContent {
    /** A pointer to a signed index. The bytes are fetched by the caller before verification. */
    data class Pointer(val document: SubscriptionPointer) : SubscriptionContent

    /** A regular plugin catalog: this address is a plugin source. */
    data object PluginCatalog : SubscriptionContent
}

/** Turns an index into the catalog shape the rest of the app already reads. */
internal fun indexAsCatalog(plugins: List<IndexedPlugin>, name: String, updatedAt: String): PluginCatalog =
    PluginCatalog(
        schemaVersion = PLUGIN_CATALOG_SCHEMA_VERSION,
        id = "subscription",
        name = name,
        description = "",
        updatedAt = updatedAt.ifBlank { "1970-01-01T00:00:00Z" },
        plugins = plugins.map { entry -> entry.plugin.copy(sourceUrl = entry.catalogUrl) },
    )

object SubscriptionParser {
    /**
     * A source address serves either a plugin catalog or a pointer to a signed index.
     * Everything a subscriber needs beyond the pointer is inside the index, so no address
     * requires a request per plugin.
     */
    fun classify(text: String, allowHttp: Boolean): SubscriptionContent {
        val json = PluginJson.parseToJsonElement(text).jsonObject
        val shapes = listOf("plugins", "indexUrl").count { json.containsKey(it) }
        require(shapes == 1) {
            "A source document must declare exactly one of plugins or indexUrl."
        }
        return if (json.containsKey("plugins")) SubscriptionContent.PluginCatalog
        else SubscriptionContent.Pointer(pointer(json, allowHttp))
    }

    fun pointer(json: JsonObject, allowHttp: Boolean): SubscriptionPointer {
        val name = json["name"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("A pointer must declare its name.")
        val indexUrl = json["indexUrl"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("A pointer must declare its indexUrl.")
        val fingerprint = json["signingKeyFingerprint"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("A pointer must declare its signingKeyFingerprint.")
        val keyId = json["keyId"]?.jsonPrimitive?.content ?: "subscription"
        PluginUrlPolicy.check(indexUrl, allowHttp)
        require(fingerprint.matches(Regex("[0-9a-f]{64}"))) {
            "A signing key fingerprint must be 64 lowercase hexadecimal characters."
        }
        return SubscriptionPointer(name, indexUrl, fingerprint, keyId)
    }

    /**
     * Verifies the index signature, then parses it.
     *
     * The signature is Ed25519 over the exact index bytes. The fingerprint in the pointer
     * must equal the SHA-256 of the raw public key, which is what the user saw when they
     * subscribed; a substituted key therefore fails here rather than silently trusting.
     */
    fun verifyIndex(pointer: SubscriptionPointer, indexBytes: ByteArray, signatureBytes: ByteArray,
        publicKeyBytes: ByteArray, allowHttp: Boolean): SubscriptionIndex {
        require(publicKeyBytes.size == 32) { "A subscription public key must be 32 bytes." }
        val actual = publicKeyBytes.toByteString().sha256().hex()
        require(actual == pointer.signingKeyFingerprint) {
            "The index signing key does not match the fingerprint in the pointer."
        }
        val signature = signatureBytes.toByteString()
        require(signature.size == 64) { "A subscription signature must be 64 bytes." }
        require(Ed25519.verify(indexBytes.toByteString(), signature, publicKeyBytes.toByteString())) {
            "The index signature does not verify."
        }
        val json = PluginJson.parseToJsonElement(indexBytes.decodeToString()).jsonObject
        val schema = json["schemaVersion"]?.jsonPrimitive?.int
            ?: throw IllegalArgumentException("The index must declare schemaVersion.")
        require(schema == 1) { "Unsupported subscription index version $schema." }
        // Each entry carries the same plugin object a catalog would, so the same
        // normalization fills its optional fields; only the release list is narrowed to one.
        val plugins = json.getValue("plugins").jsonArray.mapIndexed { index, element ->
            val entry = element.jsonObject
            val catalogUrl = entry["catalogUrl"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Index entry ${index + 1} must declare its catalogUrl.")
            PluginUrlPolicy.check(catalogUrl, allowHttp)
            val plugin = PluginJson.decodeFromJsonElement(
                CatalogPlugin.serializer(), normalizePluginJson(entry.getValue("plugin").jsonObject))
            require(plugin.availableVersions.size == 1) {
                "Index entry ${index + 1} must carry exactly its newest release."
            }
            IndexedPlugin(catalogUrl, plugin)
        }
        require(plugins.distinctBy { it.plugin.id }.size == plugins.size) { "The index lists a plugin id twice." }
        // Reuse the catalog rules rather than restating them: the same field limits, URL
        // policy, and digest format apply to an indexed plugin as to a published one.
        CatalogParser().validate(indexAsCatalog(plugins,
            json["name"]?.jsonPrimitive?.content ?: pointer.name,
            json["updatedAt"]?.jsonPrimitive?.content.orEmpty()), allowHttp)
        return SubscriptionIndex(schema, json["name"]?.jsonPrimitive?.content ?: pointer.name,
            json["updatedAt"]?.jsonPrimitive?.content ?: "", plugins)
    }

    fun publicKey(hex: String): ByteArray {
        require(hex.matches(Regex("[0-9a-f]{64}"))) { "A subscription public key must be 64 lowercase hexadecimal characters." }
        return hex.decodeHex().toByteArray()
    }
}
