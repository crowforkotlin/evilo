package crow.evilo.feature.browse

import androidx.compose.runtime.Composable
import crow.evilo.model.MediaKind
import crow.evilo.app.resources.Res
import crow.evilo.app.resources.kind_anime
import crow.evilo.app.resources.kind_film
import crow.evilo.app.resources.kind_manga
import crow.evilo.app.resources.kind_novel
import crow.evilo.app.resources.*
import crow.evilo.plugin.catalog.PluginBlock
import crow.evilo.plugin.catalog.PluginBlockCode
import crow.evilo.plugin.catalog.PluginRelease
import crow.evilo.app.PluginLoadPhase
import crow.evilo.app.LocalAppDiagnostics
import org.jetbrains.compose.resources.stringResource

@Composable
fun mediaKindLabel(kind: MediaKind): String = stringResource(when (kind) {
    MediaKind.MANGA -> Res.string.kind_manga
    MediaKind.NOVEL -> Res.string.kind_novel
    MediaKind.ANIME -> Res.string.kind_anime
    MediaKind.FILM -> Res.string.kind_film
})

@Composable
internal fun browseSearchLabels() = BrowseSearchLabels(
    overseas = stringResource(Res.string.plugin_overseas),
    domestic = stringResource(Res.string.plugin_domestic),
    nsfw = stringResource(Res.string.plugin_nsfw),
    updateAvailable = stringResource(Res.string.plugin_update_available),
    sourceChoice = stringResource(Res.string.plugin_choose_source),
    pendingSource = stringResource(Res.string.browse_pending_source),
    blocks = PluginBlockCode.entries.associateWith { pluginBlockLabel(PluginBlock(it, "")).orEmpty() },
)

@Composable
internal fun releaseActionLabel(entry: PluginEntry, release: PluginRelease): String {
    val installed = entry.installation
    return stringResource(when {
        installed == null -> Res.string.plugin_install
        release.versionCode > installed.release.versionCode -> Res.string.plugin_update
        release.versionCode < installed.release.versionCode -> Res.string.plugin_downgrade
        else -> Res.string.plugin_reinstall
    })
}

@Composable
internal fun pluginLoadPhaseLabel(phase: PluginLoadPhase?): String {
    if (!LocalAppDiagnostics.current) return stringResource(Res.string.plugin_loading)
    return pluginDebugPhaseLabel(phase)
}

@Composable
internal fun pluginDebugPhaseLabel(phase: PluginLoadPhase?): String = stringResource(when (phase) {
    PluginLoadPhase.LOADING_PACKAGE -> Res.string.plugin_phase_package
    PluginLoadPhase.FETCHING_MANIFEST -> Res.string.plugin_phase_manifest
    PluginLoadPhase.VERIFYING_PACKAGE -> Res.string.plugin_phase_verification
    PluginLoadPhase.DOWNLOADING_ARTIFACT -> Res.string.plugin_phase_download
    PluginLoadPhase.INITIALIZING_RUNTIME -> Res.string.plugin_phase_runtime
    PluginLoadPhase.NEGOTIATING -> Res.string.plugin_phase_negotiating
    PluginLoadPhase.RECORDING_INSTALLATION -> Res.string.plugin_phase_recording
    else -> Res.string.plugin_phase_precheck
})

@Composable
internal fun pluginBlockLabel(block: PluginBlock?): String? = block?.let {
    stringResource(when (it.code) {
        PluginBlockCode.SOURCE_DISABLED -> Res.string.plugin_block_source_disabled
        PluginBlockCode.SOURCE_MISSING -> Res.string.plugin_block_source_missing
        PluginBlockCode.RELEASE_WITHDRAWN -> Res.string.plugin_block_withdrawn
        PluginBlockCode.METADATA_CHANGED -> Res.string.plugin_block_metadata
        PluginBlockCode.NSFW_HIDDEN -> Res.string.plugin_block_nsfw
        PluginBlockCode.KIND_UNSUPPORTED -> Res.string.plugin_block_kind
        PluginBlockCode.MANDATORY_UPDATE -> Res.string.plugin_block_update
        PluginBlockCode.HOST_INCOMPATIBLE -> Res.string.plugin_block_host
        PluginBlockCode.PROTOCOL_INCOMPATIBLE -> Res.string.plugin_block_protocol
    })
}

