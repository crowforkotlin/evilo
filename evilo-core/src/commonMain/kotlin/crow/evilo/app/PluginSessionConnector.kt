package crow.evilo.app

import crow.evilo.feature.reader.MediaSourceSession
import crow.evilo.plugin.catalog.CatalogPlugin
import crow.evilo.plugin.catalog.PluginRelease
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class PluginLoadPhase {
    PRECHECK, LOADING_PACKAGE, FETCHING_MANIFEST, VERIFYING_PACKAGE,
    DOWNLOADING_ARTIFACT, INITIALIZING_RUNTIME, NEGOTIATING, RECORDING_INSTALLATION,
}

data class PluginLoadTiming(val phase: PluginLoadPhase, val startedAtMs: Long, val finishedAtMs: Long? = null,
    val started: TimeMark = TimeSource.Monotonic.markNow())

fun interface PluginSessionConnector {
    suspend fun connect(plugin: CatalogPlugin, release: PluginRelease, allowHttp: Boolean,
        onPhase: (PluginLoadPhase) -> Unit): MediaSourceSession

    suspend fun connectInstalled(plugin: CatalogPlugin, release: PluginRelease, allowHttp: Boolean,
        approvedManifestSha256: String?, onPhase: (PluginLoadPhase) -> Unit): MediaSourceSession =
        connect(plugin, release, allowHttp, onPhase)
}
