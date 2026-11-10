package crow.evilo.plugin.api

import crow.evilo.model.MediaChapter
import crow.evilo.model.MediaContent
import crow.evilo.model.MediaItem
import crow.evilo.model.MediaKind
import crow.wasmline.WasmlineService
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PLUGIN_PROTOCOL_MAJOR = 1
const val PLUGIN_PROTOCOL_MINOR = 0
const val HOST_HTTP_CAPABILITY = "http.request.v1"
const val HOST_STORAGE_CAPABILITY = "storage.kv.v1"
const val HOST_MEDIA_CAPABILITY = "media.catalog.v1"

val PluginJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// JSON envelopes keep additive protocol fields independent of the generated Wasmline bridge ABI.
interface EviloPluginService : WasmlineService {
    fun describe(): String
    fun start(operationJson: String): String
    fun resume(operationJson: String, context: String, responseJson: String): String
}

interface EviloHostService : WasmlineService {
    fun describeHost(): String
    fun readValue(key: String): String?
    fun writeValue(key: String, value: String): Boolean
    fun removeValue(key: String): Boolean
    fun listKeys(prefix: String): String
}

@Serializable
data class ProtocolRequirement(
    val major: Int = PLUGIN_PROTOCOL_MAJOR,
    val minimumMinor: Int = PLUGIN_PROTOCOL_MINOR,
    val requiredCapabilities: Set<String> = setOf(HOST_MEDIA_CAPABILITY),
    val optionalCapabilities: Set<String> = emptySet(),
)

@Serializable
data class HttpAccess(
    val origins: Set<String> = emptySet(),
    val pathPrefixes: Set<String> = setOf("/"),
    val methods: Set<String> = setOf("GET"),
    val headers: Set<String> = setOf("accept", "user-agent"),
)

@Serializable
data class PluginPermissions(
    val api: HttpAccess = HttpAccess(),
    val media: HttpAccess = HttpAccess(),
    val storage: Boolean = false,
)

@Serializable
data class PluginDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val kinds: Set<MediaKind>,
    val languages: List<String>,
    val protocol: ProtocolRequirement = ProtocolRequirement(),
    val permissions: PluginPermissions = PluginPermissions(),
    val extensions: Map<String, String> = emptyMap(),
)

@Serializable
data class HostDescriptor(
    val appVersion: String,
    val appVersionCode: Long,
    val protocolMajor: Int = PLUGIN_PROTOCOL_MAJOR,
    val protocolMinor: Int = PLUGIN_PROTOCOL_MINOR,
    val capabilities: Set<String>,
    val storageQuotaBytes: Int = 1024 * 1024,
    val storageValueLimitBytes: Int = 64 * 1024,
    val storageEntryLimit: Int = 256,
)

@Serializable
enum class PluginOperationKind {
    @SerialName("search") SEARCH,
    @SerialName("details") DETAILS,
    @SerialName("chapters") CHAPTERS,
    @SerialName("content") CONTENT,
}

@Serializable
data class PluginOperation(
    val operation: PluginOperationKind,
    val kind: MediaKind,
    val query: String = "",
    val page: Int = 1,
    val item: MediaItem? = null,
    val chapter: MediaChapter? = null,
    val extensions: Map<String, String> = emptyMap(),
)

@Serializable
data class PluginResult(
    val items: List<MediaItem> = emptyList(),
    val hasNextPage: Boolean = false,
    val details: MediaItem? = null,
    val chapters: List<MediaChapter> = emptyList(),
    val content: MediaContent? = null,
    val extensions: Map<String, String> = emptyMap(),
)

@Serializable
data class PluginStep(
    val request: SourceRequest? = null,
    val context: String = "",
    val result: PluginResult? = null,
    val batch: PluginResult? = null,
) {
    init {
        require((request == null) != (result == null)) { "A plugin step must contain either an HTTP request or a final result." }
        require(batch == null || request != null) { "Intermediate batches require a continuation request." }
    }
}

interface MediaSource {
    val descriptor: PluginDescriptor
    suspend fun execute(operation: PluginOperation): PluginResult
    suspend fun close()
}
