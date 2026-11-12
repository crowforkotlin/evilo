package crow.evilo.plugin.catalog

enum class PluginBlockCode {
    SOURCE_DISABLED, SOURCE_MISSING, RELEASE_WITHDRAWN, METADATA_CHANGED, NSFW_HIDDEN,
    KIND_UNSUPPORTED, MANDATORY_UPDATE, HOST_INCOMPATIBLE, PROTOCOL_INCOMPATIBLE,
}

data class PluginBlock(val code: PluginBlockCode, val message: String)

data class SourceCheckState(
    val running: Boolean = false,
    val lastSuccessAt: String? = null,
    val error: String? = null,
    val usingCache: Boolean = false,
)
