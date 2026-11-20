package crow.evilo.feature.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import crow.evilo.app.EviloImages
import crow.evilo.app.LocalAppDiagnostics
import crow.evilo.app.resources.*
import crow.evilo.plugin.catalog.PluginBlockCode
import crow.evilo.feature.reader.ImageFailure
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloImageErrorDetails
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloMotion
import crow.evilo.ui.EviloSettingsGroup
import crow.evilo.ui.EviloSpacing
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PluginDetailScreen(state: PluginHomeState, entry: PluginEntry, images: EviloImages, dispatch: (PluginHomeIntent) -> Unit) {
    val plugin = entry.plugin
    val installed = entry.installation
    val copies = state.entries.values.filter { it.plugin.id == plugin.id }
    var versionCode by rememberSaveable(entry.key) { mutableStateOf(entry.recommended?.versionCode ?: installed?.release?.versionCode) }
    var changedSelection by rememberSaveable(entry.key) { mutableStateOf(false) }
    var iconAttempt by remember(entry.key) { mutableStateOf(0) }
    var iconFailure by remember(entry.key) { mutableStateOf<ImageFailure?>(null) }
    var showIconError by remember(entry.key) { mutableStateOf(false) }
    LaunchedEffect(entry.releases, entry.recommended) {
        if (!changedSelection || entry.releases.none { it.release.versionCode == versionCode })
            versionCode = entry.recommended?.versionCode ?: installed?.release?.versionCode
    }
    val choice = entry.releases.firstOrNull { it.release.versionCode == versionCode }
    val release = choice?.release
    val sameRelease = release != null && installed?.release == release && installed?.approvedManifestSha256 != null
    val canBrowse = installed != null && entry.openBlock == null
    val source = state.catalog.subscriptions.firstOrNull { it.id == entry.sourceId }
    val blocked = pluginBlockLabel(choice?.block) ?: choice?.blocked
    val canInstall = release != null && choice?.blocked == null && !sameRelease && state.canMutate
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(EviloLayout.PagePadding),
            verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
            item(key = "identity") {
                Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
                        PluginAvatar(plugin.name, plugin.iconUrl, entry.allowHttp, images, iconAttempt) { iconFailure = it }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                            Text(plugin.name, style = MaterialTheme.typography.titleLarge)
                            PluginMetadata(stringResource(Res.string.plugin_authors, plugin.authors.joinToString { it.name }))
                        }
                    }
                    if (iconFailure != null) TextButton(onClick = { showIconError = true }) { Text(stringResource(Res.string.image_icon_details)) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                        PluginBadge(stringResource(if (plugin.overseas) Res.string.plugin_overseas else Res.string.plugin_domestic))
                        if (plugin.nsfw || state.catalog.isPluginNsfw(plugin.id)) PluginBadge(stringResource(Res.string.plugin_nsfw), warning = true)
                    }
                    Text(plugin.description, style = MaterialTheme.typography.bodyMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                        SelectionContainer { PluginMetadata(stringResource(Res.string.plugin_package, plugin.id)) }
                        PluginMetadata(stringResource(Res.string.plugin_content_types, plugin.kinds.map { mediaKindLabel(it) }.joinToString(" / ")))
                        PluginMetadata(stringResource(Res.string.plugin_languages, plugin.languages.joinToString()))
                        PluginMetadata(stringResource(Res.string.plugin_created, plugin.createdAt.substringBefore('T')))
                        PluginMetadata(stringResource(Res.string.plugin_updated, plugin.updatedAt.substringBefore('T')))
                    }
                }
            }
            item(key = "installation") {
                EviloSettingsGroup(stringResource(Res.string.plugin_installation)) {
                    Column(Modifier.padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                        if (installed == null) PluginBadge(stringResource(Res.string.plugin_not_installed))
                        installed?.let {
                            PluginBadge(stringResource(Res.string.plugin_installed_version, it.release.version))
                            PluginMetadata(stringResource(Res.string.plugin_installed_at, it.installedAt))
                            PluginMetadata(stringResource(Res.string.plugin_published_at, it.release.publishedAt.substringBefore('T')))
                            PluginMetadata(stringResource(Res.string.plugin_content_types, it.release.kinds.map { kind -> mediaKindLabel(kind) }.joinToString(" / ")))
                            PluginMetadata(stringResource(Res.string.plugin_languages, it.release.languages.joinToString()))
                        }
                        PluginMetadata(stringResource(Res.string.plugin_source_name, sourceName(state, entry.sourceId)))
                        SelectionContainer { PluginMetadata(source?.url ?: installed?.sourceUrl.orEmpty()) }
                        pluginBlockLabel(entry.openBlock)?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error) }
                        if (entry.openBlock?.code == PluginBlockCode.HOST_INCOMPATIBLE) installed?.let {
                            PluginMetadata(stringResource(Res.string.plugin_version_requirements, it.release.minimumHostVersionCode,
                                it.release.maximumHostVersionCode?.toString() ?: "*"))
                        }
                        SourceCheckStatus(state, entry.sourceId)
                        OutlinedButton(onClick = { dispatch(PluginHomeIntent.ShowSourceSettings(entry.sourceId)) },
                            modifier = Modifier.fillMaxWidth(), enabled = source != null) { Text(stringResource(Res.string.source_settings)) }
                    }
                }
            }
            if (copies.size > 1) item(key = "copies") {
                EviloSettingsGroup(stringResource(Res.string.plugin_source_copies)) {
                    Column(Modifier.padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                        if (hasReleaseConflict(copies)) Text(stringResource(Res.string.plugin_conflicting_sources),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        copies.sortedBy { copy -> state.catalog.subscriptions.indexOfFirst { it.id == copy.sourceId }.takeIf { it >= 0 } ?: Int.MAX_VALUE }.forEachIndexed { index, copy ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            PluginSourceCopy(state, copy, dispatch)
                        }
                        val nsfwSources = copies.filter { copy -> copy.installation?.let {
                            it.plugin.nsfw || copy.listing?.plugin?.nsfw == true
                        } == true }.map { sourceName(state, it.sourceId) }
                        if (nsfwSources.isNotEmpty()) PluginMetadata(stringResource(Res.string.plugin_nsfw_sources, nsfwSources.joinToString()))
                        if (copies.count { it.installation != null } > 1) PluginMetadata(stringResource(Res.string.plugin_shared_data))
                    }
                }
            }
            item(key = "versions-heading") { Text(stringResource(Res.string.plugin_versions), style = MaterialTheme.typography.titleMedium) }
            items(entry.releases, key = { it.release.versionCode }) { option ->
                val selected = option.release.versionCode == versionCode
                val selectable = option.block == null || option.release == installed?.release
                val container by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    EviloMotion.componentSpec(), label = "release-container")
                val outline by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                    EviloMotion.componentSpec(), label = "release-outline")
                Surface(onClick = { versionCode = option.release.versionCode; changedSelection = true }, enabled = selectable && state.canMutate,
                    modifier = Modifier.semantics { this.selected = selected }.animateContentSize(EviloMotion.componentSpec()), shape = MaterialTheme.shapes.medium,
                    color = container, border = BorderStroke(EviloLayout.BorderWidth, outline)) {
                    Row(Modifier.fillMaxWidth().padding(EviloSpacing.Medium), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, onClick = null, enabled = selectable && state.canMutate)
                        Column(Modifier.weight(1f).padding(start = EviloSpacing.Small), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                            Text(stringResource(if (option.release == installed?.release) Res.string.plugin_installed_version else Res.string.plugin_version,
                                option.release.version), style = MaterialTheme.typography.titleSmall)
                            PluginMetadata(stringResource(Res.string.plugin_published_at, option.release.publishedAt.substringBefore('T')))
                            PluginMetadata(stringResource(Res.string.plugin_version_requirements, option.release.minimumHostVersionCode,
                                option.release.maximumHostVersionCode?.toString() ?: "*"))
                            if (selected) Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                                PluginMetadata(stringResource(Res.string.plugin_version_code, option.release.versionCode))
                                PluginMetadata(stringResource(Res.string.plugin_content_types, option.release.kinds.map { mediaKindLabel(it) }.joinToString(" / ")))
                                PluginMetadata(stringResource(Res.string.plugin_languages, option.release.languages.joinToString()))
                            }
                            if (option.release == entry.recommended) PluginBadge(stringResource(Res.string.plugin_recommended_release))
                            (pluginBlockLabel(option.block) ?: option.blocked)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            item(key = "source-code") {
                Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                    Text(stringResource(Res.string.plugin_source_code), style = MaterialTheme.typography.titleMedium)
                    installed?.let {
                        Text(stringResource(Res.string.plugin_installed_version, it.release.version), style = MaterialTheme.typography.titleSmall)
                        PluginSourceLinks(it.release.sourceUrl, it.release)
                    }
                    if (release != null && release != installed?.release) {
                        Text(stringResource(Res.string.plugin_selected_version, release.version), style = MaterialTheme.typography.titleSmall)
                        PluginSourceLinks(release.sourceUrl, release)
                    }
                    if (release == null && installed == null) PluginSourceLinks(plugin.sourceUrl, null)
                }
            }
            item(key = "safety") {
                EviloSettingsGroup(stringResource(Res.string.plugin_safety_group)) {
                    Column(Modifier.padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                        Text(stringResource(Res.string.plugin_safety), style = MaterialTheme.typography.bodySmall)
                        if (entry.allowHttp) Text(stringResource(Res.string.plugin_http_warning), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        installed?.let {
                            Text(stringResource(Res.string.plugin_installed_version, it.release.version), style = MaterialTheme.typography.titleSmall)
                            PluginSigningText(it.release, entry.releases.firstOrNull { choice -> choice.release == it.release }?.officialSigner ?: (it.release.signing == null))
                            it.approvedManifestSha256?.takeIf { digest -> digest != it.release.manifestSha256 }?.let { digest ->
                                SelectionContainer { PluginMetadata(stringResource(Res.string.plugin_manifest_hash, digest)) }
                            }
                        }
                        if (release != null && release != installed?.release) {
                            Text(stringResource(Res.string.plugin_selected_version, release.version), style = MaterialTheme.typography.titleSmall)
                            PluginSigningText(release, choice?.officialSigner == true)
                        }
                    }
                }
            }
            if (installed != null) item(key = "uninstall") {
                Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                    PluginMetadata(stringResource(Res.string.plugin_uninstall_source_name, sourceName(state, installed.sourceId)))
                    Button(onClick = { dispatch(PluginHomeIntent.RequestUninstall(entry.key)) }, enabled = state.canMutate,
                        modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                        Text(stringResource(Res.string.plugin_uninstall))
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().animateContentSize(EviloMotion.componentSpec()).padding(EviloSpacing.Large),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                if (state.connection.installationKey == entry.key) {
                    if (state.connection.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(Res.string.plugin_installing), style = MaterialTheme.typography.bodySmall)
                    }
                    var showLoadDiagnostics by remember(entry.key) { mutableStateOf(false) }
                    if (LocalAppDiagnostics.current && state.connection.loadTimings.isNotEmpty()) {
                        TextButton(onClick = { showLoadDiagnostics = true }) { Text(stringResource(Res.string.plugin_debug_timings)) }
                        if (showLoadDiagnostics) PluginLoadDiagnosticsDialog(state.connection.loadTimings) { showLoadDiagnostics = false }
                    }
                }
                if (!canBrowse && blocked != null) Text(blocked, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (release == null) PluginMetadata(stringResource(Res.string.plugin_no_compatible_version))
                if (source?.enabled == false) Button(onClick = { dispatch(PluginHomeIntent.EnableSource(entry.sourceId, true)) },
                    modifier = Modifier.fillMaxWidth(), enabled = state.canMutate) { Text(stringResource(Res.string.source_enable)) }
                if (canBrowse) Button(onClick = { dispatch(PluginHomeIntent.OpenPlugin(entry.key,
                    rememberSource = (state.page as? HomePage.PluginDetail)?.rememberSourceOnOpen ?: false)) },
                    modifier = Modifier.fillMaxWidth(), enabled = state.canMutate) { Text(stringResource(Res.string.plugin_browse)) }
                if (!sameRelease && release != null && source?.enabled == true) {
                    if (canBrowse) OutlinedButton(onClick = { dispatch(PluginHomeIntent.ReviewRelease(entry.key, release.versionCode)) },
                        modifier = Modifier.fillMaxWidth(), enabled = canInstall) { Text(releaseActionLabel(entry, release)) }
                    else Button(onClick = { dispatch(PluginHomeIntent.ReviewRelease(entry.key, release.versionCode)) },
                        modifier = Modifier.fillMaxWidth(), enabled = canInstall) { Text(releaseActionLabel(entry, release)) }
                    if (installed == null) OutlinedButton(onClick = {
                        dispatch(PluginHomeIntent.ReviewRelease(entry.key, release.versionCode, openAfterInstall = true))
                    }, modifier = Modifier.fillMaxWidth(), enabled = canInstall) { Text(stringResource(Res.string.plugin_install_browse)) }
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                    TextButton(onClick = { dispatch(PluginHomeIntent.RefreshSource(entry.sourceId)) }, enabled = source?.enabled == true && !state.catalog.refreshing) {
                        Text(stringResource(Res.string.source_check_updates))
                    }
                    if (state.connection.busy) TextButton(onClick = { dispatch(PluginHomeIntent.ClosePlugin) }) { Text(stringResource(Res.string.home_cancel)) }
                }
            }
        }
    }
    iconFailure?.takeIf { showIconError }?.let { failure -> EviloImageErrorDetails(failure, onDismiss = { showIconError = false },
        onRetry = { iconAttempt++ }) }
}

@Composable
private fun PluginMetadata(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginSourceCopy(state: PluginHomeState, entry: PluginEntry, dispatch: (PluginHomeIntent) -> Unit) {
    val source = state.catalog.subscriptions.firstOrNull { it.id == entry.sourceId }
    val installed = entry.installation
    val preferred = preferredInstallationKey(state.catalog, entry.plugin.id) == entry.key
    Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(sourceName(state, entry.sourceId), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { dispatch(PluginHomeIntent.ShowSourceCatalog(entry.sourceId)) }, enabled = source != null) {
                Icon(EviloIcons.ChevronRight, stringResource(Res.string.source_catalog_for, sourceName(state, entry.sourceId)))
            }
        }
        SelectionContainer { PluginMetadata(source?.url ?: installed?.sourceUrl.orEmpty()) }
        PluginMetadata(if (installed == null) stringResource(Res.string.plugin_only_catalog)
            else stringResource(Res.string.plugin_installed_version, installed.release.version))
        if (source?.enabled == false) PluginBadge(stringResource(Res.string.source_disabled))
        else if (source == null) PluginBadge(stringResource(Res.string.plugin_block_source_missing))
        pluginBlockLabel(entry.openBlock)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (preferred && installed != null) PluginBadge(stringResource(Res.string.plugin_primary_source))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
            if (installed != null && !preferred) OutlinedButton(onClick = { dispatch(PluginHomeIntent.SelectPreferredInstallation(entry.key)) },
                enabled = state.canMutate && copyCanOpen(state, entry)) { Text(stringResource(Res.string.plugin_use_source)) }
            if ((state.page as? HomePage.PluginDetail)?.key != entry.key) TextButton(onClick = { dispatch(PluginHomeIntent.ManagePlugin(entry.key)) }) {
                Text(stringResource(Res.string.plugin_view_source_copy))
            }
        }
    }
}

@Composable
internal fun ChoosePluginSourceScreen(state: PluginHomeState, id: String, images: EviloImages, dispatch: (PluginHomeIntent) -> Unit) {
    val copies = state.entries.values.filter { it.installation?.plugin?.id == id }
        .sortedBy { copy -> state.catalog.subscriptions.indexOfFirst { it.id == copy.sourceId }.takeIf { it >= 0 } ?: Int.MAX_VALUE }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(EviloLayout.PagePadding),
        verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
        item { Text(stringResource(Res.string.plugin_choose_source_description), style = MaterialTheme.typography.bodyMedium) }
        if (hasReleaseConflict(copies)) item { Text(stringResource(Res.string.plugin_conflicting_sources), color = MaterialTheme.colorScheme.error) }
        items(copies, key = { it.key }) { copy ->
            EviloSettingsGroup(sourceName(state, copy.sourceId)) {
                Column(Modifier.padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
                        PluginAvatar(copy.plugin.name, copy.plugin.iconUrl, copy.allowHttp, images)
                        Text(copy.plugin.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    }
                    PluginMetadata(stringResource(Res.string.plugin_installed_version, checkNotNull(copy.installation).release.version))
                    pluginBlockLabel(copy.openBlock)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { dispatch(PluginHomeIntent.SelectPreferredInstallation(copy.key)) },
                        modifier = Modifier.fillMaxWidth(), enabled = state.canMutate && copyCanOpen(state, copy)) { Text(stringResource(Res.string.plugin_use_source)) }
                    TextButton(onClick = { dispatch(PluginHomeIntent.ManagePlugin(copy.key)) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(Res.string.plugin_details))
                    }
                }
            }
        }
    }
}

private fun copyCanOpen(state: PluginHomeState, entry: PluginEntry): Boolean {
    val installed = entry.installation ?: return false
    return entry.openBlock == null && state.catalog.subscriptions.any { it.id == entry.sourceId && it.enabled } &&
        state.catalog.selectedKind in installed.release.kinds
}
