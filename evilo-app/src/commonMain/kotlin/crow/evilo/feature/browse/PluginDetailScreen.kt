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
import crow.evilo.plugin.api.PluginPermissions
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
    val sameRelease = release != null && installed?.release == release
    val canBrowse = installed != null && entry.openBlock == null
    val source = state.catalog.subscriptions.firstOrNull { it.id == entry.sourceId }
    val blocked = pluginBlockLabel(choice?.block) ?: choice?.blocked
    val canInstall = release != null && choice?.blocked == null && !sameRelease && state.canMutate
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(EviloLayout.PagePadding),
            verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
            item {
                EviloSettingsGroup(stringResource(Res.string.plugin_details)) {
                    Column(Modifier.padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
                            PluginAvatar(plugin.name, plugin.iconUrl, entry.allowHttp, images, iconAttempt) { iconFailure = it }
                            Column(Modifier.weight(1f)) {
                                Text(plugin.name, style = MaterialTheme.typography.titleLarge)
                                Text(plugin.authors.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (iconFailure != null) TextButton(onClick = { showIconError = true }) { Text(stringResource(Res.string.image_icon_details)) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                            PluginBadge(stringResource(if (plugin.overseas) Res.string.plugin_overseas else Res.string.plugin_domestic))
                            if (plugin.nsfw || state.catalog.isPluginNsfw(plugin.id)) PluginBadge(stringResource(Res.string.plugin_nsfw), warning = true)
                            plugin.languages.forEach { PluginBadge(it) }
                        }
                        Text(plugin.description, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(Res.string.plugin_availability_declared), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(plugin.kinds.map { mediaKindLabel(it) }.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                        SelectionContainer { Text(plugin.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(stringResource(Res.string.plugin_created, plugin.createdAt.substringBefore('T')), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(Res.string.plugin_updated, plugin.updatedAt.substringBefore('T')), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                EviloSettingsGroup(stringResource(if (installed != null) Res.string.more_installed else Res.string.plugin_not_installed)) {
                    Column(Modifier.padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                        installed?.let {
                            PluginBadge(stringResource(Res.string.plugin_installed_version, it.release.version))
                            Text(stringResource(Res.string.plugin_installed_at, it.installedAt), style = MaterialTheme.typography.bodySmall)
                            Text(it.release.kinds.map { kind -> mediaKindLabel(kind) }.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(sourceName(state, entry.sourceId), style = MaterialTheme.typography.bodyMedium)
                        pluginBlockLabel(entry.openBlock)?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error) }
                        if (entry.openBlock?.code == PluginBlockCode.SOURCE_DISABLED) OutlinedButton(
                            onClick = { dispatch(PluginHomeIntent.EnableSource(entry.sourceId, true)) }, enabled = state.canMutate) {
                            Text(stringResource(Res.string.source_enable))
                        }
                        if (entry.openBlock?.code == PluginBlockCode.HOST_INCOMPATIBLE) installed?.let {
                            Text(stringResource(Res.string.plugin_version_requirements, it.release.minimumHostVersionCode,
                                it.release.maximumHostVersionCode?.toString() ?: "*", it.release.protocol.major, it.release.protocol.minimumMinor),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        SourceCheckStatus(state, entry.sourceId)
                    }
                }
            }
            item {
                EviloSettingsGroup(stringResource(Res.string.plugin_source_copies)) {
                    Column(Modifier.padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                        if (hasReleaseConflict(copies)) Text(stringResource(Res.string.plugin_conflicting_sources),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        copies.sortedBy { copy -> state.catalog.subscriptions.indexOfFirst { it.id == copy.sourceId }.takeIf { it >= 0 } ?: Int.MAX_VALUE }.forEach { copy ->
                            PluginSourceCopy(state, copy, dispatch)
                        }
                        val nsfwSources = copies.filter { copy -> copy.installation?.let {
                            it.plugin.nsfw || copy.listing?.plugin?.nsfw == true
                        } == true }.map { sourceName(state, it.sourceId) }
                        if (nsfwSources.isNotEmpty()) Text(stringResource(Res.string.plugin_nsfw_sources, nsfwSources.joinToString()),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (copies.count { it.installation != null } > 1) Text(stringResource(Res.string.plugin_shared_data),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Text(stringResource(Res.string.plugin_versions), style = MaterialTheme.typography.titleMedium) }
            items(entry.releases, key = { it.release.versionCode }) { option ->
                val selected = option.release.versionCode == versionCode
                val selectable = option.block == null || option.release == installed?.release
                val container by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    EviloMotion.componentSpec(), label = "release-container")
                val outline by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    EviloMotion.componentSpec(), label = "release-outline")
                Surface(onClick = { versionCode = option.release.versionCode; changedSelection = true }, enabled = selectable && state.canMutate,
                    modifier = Modifier.semantics { this.selected = selected }, shape = MaterialTheme.shapes.medium,
                    color = container,
                    border = BorderStroke(EviloLayout.BorderWidth, outline)) {
                    Row(Modifier.fillMaxWidth().padding(EviloSpacing.Medium), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, onClick = null, enabled = selectable && state.canMutate)
                        Column(Modifier.weight(1f).padding(start = EviloSpacing.Small), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                            Text(stringResource(if (option.release == installed?.release) Res.string.plugin_installed_version else Res.string.plugin_version,
                                option.release.version), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(Res.string.plugin_version_requirements, option.release.minimumHostVersionCode,
                                option.release.maximumHostVersionCode?.toString() ?: "*", option.release.protocol.major, option.release.protocol.minimumMinor),
                                style = MaterialTheme.typography.bodySmall)
                            Text(option.release.kinds.map { mediaKindLabel(it) }.joinToString(" / ") + " | " + option.release.languages.joinToString(),
                                style = MaterialTheme.typography.bodySmall)
                            if (option.release == entry.recommended) PluginBadge(stringResource(Res.string.plugin_recommended_release))
                            pluginBlockLabel(option.block)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            item {
                EviloSettingsGroup(stringResource(Res.string.plugin_permissions_group)) {
                    Column(Modifier.animateContentSize(EviloMotion.componentSpec()).padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                        installed?.let {
                            Text(stringResource(Res.string.plugin_approved_permissions, it.release.version), style = MaterialTheme.typography.titleSmall)
                            ReleasePermissionText(it.release.permissions)
                        }
                        if (release != null && (installed == null || release.permissions != installed.release.permissions)) {
                            Text(stringResource(Res.string.plugin_selected_permissions, release.version), style = MaterialTheme.typography.titleSmall)
                            ReleasePermissionText(release.permissions)
                            if (installed != null) Text(stringResource(Res.string.plugin_permissions_changed), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                        Text(stringResource(Res.string.plugin_safety), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                EviloSettingsGroup(stringResource(Res.string.plugin_source_code)) {
                    Column(Modifier.animateContentSize(EviloMotion.componentSpec()).padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                        installed?.let {
                            Text(stringResource(Res.string.plugin_installed_version, it.release.version), style = MaterialTheme.typography.titleSmall)
                            PluginSourceLinks(it.release.sourceUrl, it.release)
                        }
                        if (release != null && release != installed?.release) {
                            Text(stringResource(Res.string.plugin_version, release.version), style = MaterialTheme.typography.titleSmall)
                            PluginSourceLinks(release.sourceUrl, release)
                        }
                        if (release == null && installed == null) PluginSourceLinks(plugin.sourceUrl, null)
                    }
                }
            }
            if (installed != null) item {
                EviloSettingsGroup(stringResource(Res.string.plugin_uninstall)) {
                    Column(Modifier.padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                        Text(sourceName(state, installed.sourceId), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { dispatch(PluginHomeIntent.RequestUninstall(entry.key)) }, enabled = state.canMutate,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(Res.string.plugin_uninstall)) }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.fillMaxWidth().animateContentSize(EviloMotion.componentSpec()).padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
            if (state.connection.installationKey == entry.key) {
                if (state.connection.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(pluginLoadPhaseLabel(state.connection.phase), style = MaterialTheme.typography.bodySmall)
                }
                var showLoadDiagnostics by remember(entry.key) { mutableStateOf(false) }
                if (LocalAppDiagnostics.current && state.connection.loadTimings.isNotEmpty()) {
                    TextButton(onClick = { showLoadDiagnostics = true }) { Text(stringResource(Res.string.plugin_debug_timings)) }
                    if (showLoadDiagnostics) PluginLoadDiagnosticsDialog(state.connection.loadTimings) { showLoadDiagnostics = false }
                }
            }
            if (!canBrowse && blocked != null) Text(blocked, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (release == null) Text(stringResource(Res.string.plugin_no_compatible_version), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                if (source?.enabled == false) Button(onClick = { dispatch(PluginHomeIntent.EnableSource(entry.sourceId, true)) }, enabled = state.canMutate) {
                    Text(stringResource(Res.string.source_enable))
                }
                if (canBrowse) Button(onClick = { dispatch(PluginHomeIntent.OpenPlugin(entry.key,
                    rememberSource = (state.page as? HomePage.PluginDetail)?.rememberSourceOnOpen ?: false)) }, enabled = state.canMutate) {
                    Text(stringResource(Res.string.plugin_browse))
                }
                if (!sameRelease && release != null && source?.enabled == true) {
                    if (canBrowse) OutlinedButton(onClick = { dispatch(PluginHomeIntent.ReviewRelease(entry.key, release.versionCode)) }, enabled = canInstall) {
                        Text(releaseActionLabel(entry, release))
                    } else Button(onClick = { dispatch(PluginHomeIntent.ReviewRelease(entry.key, release.versionCode)) }, enabled = canInstall) {
                        Text(releaseActionLabel(entry, release))
                    }
                    if (installed == null) OutlinedButton(onClick = {
                        dispatch(PluginHomeIntent.ReviewRelease(entry.key, release.versionCode, openAfterInstall = true))
                    }, enabled = canInstall) { Text(stringResource(Res.string.plugin_install_browse)) }
                }
                TextButton(onClick = { dispatch(PluginHomeIntent.RefreshSource(entry.sourceId)) }, enabled = source?.enabled == true && !state.catalog.refreshing) {
                    Text(stringResource(Res.string.source_check_updates))
                }
                if (state.connection.busy) TextButton(onClick = { dispatch(PluginHomeIntent.ClosePlugin) }) { Text(stringResource(Res.string.home_cancel)) }
            }
        }
    }
    iconFailure?.takeIf { showIconError }?.let { failure -> EviloImageErrorDetails(failure, onDismiss = { showIconError = false },
        onRetry = { iconAttempt++ }) }
}

@Composable
private fun PluginSourceCopy(state: PluginHomeState, entry: PluginEntry, dispatch: (PluginHomeIntent) -> Unit) {
    val source = state.catalog.subscriptions.firstOrNull { it.id == entry.sourceId }
    val installed = entry.installation
    val preferred = preferredInstallationKey(state.catalog, entry.plugin.id) == entry.key
    Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(sourceName(state, entry.sourceId), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { dispatch(PluginHomeIntent.ShowSourceCatalog(entry.sourceId)) }, enabled = source != null) {
                Icon(EviloIcons.ChevronRight, stringResource(Res.string.home_manage_sources))
            }
        }
        SelectionContainer { Text(source?.url ?: installed?.sourceUrl.orEmpty(), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(if (installed == null) stringResource(Res.string.plugin_only_catalog)
            else stringResource(Res.string.plugin_installed_version, installed.release.version), style = MaterialTheme.typography.bodySmall)
        if (source?.enabled == false) PluginBadge(stringResource(Res.string.source_disabled))
        else if (source == null) PluginBadge(stringResource(Res.string.plugin_block_source_missing))
        pluginBlockLabel(entry.openBlock)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if ((state.page as? HomePage.PluginDetail)?.key != entry.key) TextButton(onClick = { dispatch(PluginHomeIntent.ManagePlugin(entry.key)) }) {
            Text(stringResource(Res.string.plugin_view_source_copy))
        }
        if (preferred && installed != null) PluginBadge(stringResource(Res.string.plugin_primary_source))
        if (installed != null && !preferred) OutlinedButton(onClick = { dispatch(PluginHomeIntent.SelectPreferredInstallation(entry.key)) },
            enabled = state.canMutate && copyCanOpen(state, entry)) {
            Text(stringResource(Res.string.plugin_use_source))
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
                    Text(stringResource(Res.string.plugin_installed_version, checkNotNull(copy.installation).release.version),
                        style = MaterialTheme.typography.bodySmall)
                    pluginBlockLabel(copy.openBlock)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { dispatch(PluginHomeIntent.SelectPreferredInstallation(copy.key)) }, enabled = state.canMutate && copyCanOpen(state, copy)) {
                        Text(stringResource(Res.string.plugin_use_source))
                    }
                    TextButton(onClick = { dispatch(PluginHomeIntent.ManagePlugin(copy.key)) }) { Text(stringResource(Res.string.plugin_details)) }
                }
            }
        }
    }
}

@Composable
private fun ReleasePermissionText(permissions: PluginPermissions) {
    NetworkPermissionText(stringResource(Res.string.plugin_api_origins), permissions.api)
    NetworkPermissionText(stringResource(Res.string.plugin_media_origins), permissions.media)
    Text(stringResource(if (permissions.storage) Res.string.plugin_storage else Res.string.plugin_no_storage), style = MaterialTheme.typography.bodySmall)
}

private fun copyCanOpen(state: PluginHomeState, entry: PluginEntry): Boolean {
    val installed = entry.installation ?: return false
    return entry.openBlock == null && state.catalog.subscriptions.any { it.id == entry.sourceId && it.enabled } &&
        state.catalog.selectedKind in installed.release.kinds
}
