package crow.evilo.feature.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import crow.evilo.app.EviloImages
import crow.evilo.app.resources.*
import crow.evilo.plugin.api.HttpAccess
import crow.evilo.plugin.catalog.OFFICIAL_SOURCE_ID
import crow.evilo.plugin.catalog.PluginRelease
import crow.evilo.plugin.catalog.PluginRepositoryState
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloSpacing
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PluginSettingsSheet(entry: PluginEntry, images: EviloImages, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onReview: (PluginRelease) -> Unit, onOpen: () -> Unit, onUninstall: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val plugin = entry.plugin
    val installed = entry.installation
    var release by remember(entry.key) { mutableStateOf(entry.recommended ?: installed?.release) }
    LaunchedEffect(entry.releases) {
        if (entry.releases.none { it.release == release }) release = entry.recommended ?: installed?.release
    }
    val selected = release
    val choice = entry.releases.firstOrNull { it.release == selected }
    val blocked = if (selected == null) stringResource(Res.string.plugin_no_compatible_version)
        else if (choice == null) stringResource(Res.string.plugin_release_changed) else choice.blocked
    val sameVersion = selected != null && installed?.release == selected
    val canOpen = installed != null && entry.openBlockReason == null
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), sheetMaxWidth = EviloLayout.SheetMaxWidth) {
        Column(Modifier.fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), state = rememberLazyListState(),
                contentPadding = PaddingValues(start = EviloSpacing.ExtraLarge, end = EviloSpacing.ExtraLarge, bottom = EviloSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                        Text(stringResource(Res.string.plugin_settings), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
                            PluginAvatar(plugin.name, plugin.iconUrl, entry.allowHttp, images)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                                Text(plugin.name, style = MaterialTheme.typography.titleLarge)
                                Text(plugin.authors.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                        }
                        Text(plugin.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                            installed?.let { PluginBadge(stringResource(Res.string.plugin_installed_version, it.release.version)) }
                            PluginBadge(stringResource(if (entry.officiallyCertified) Res.string.plugin_official else Res.string.plugin_community))
                            PluginBadge(stringResource(if (plugin.overseas) Res.string.plugin_overseas else Res.string.plugin_domestic))
                            if (plugin.nsfw) PluginBadge(stringResource(Res.string.plugin_nsfw), warning = true)
                        }
                        Text(plugin.kinds.map { mediaKindLabel(it) }.joinToString(" / ") + " | " + plugin.languages.joinToString(),
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Text(stringResource(Res.string.plugin_created, plugin.createdAt.substringBefore('T')), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Text(stringResource(Res.string.plugin_updated, plugin.updatedAt.substringBefore('T')), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        if (plugin.forceUpdate) Text(stringResource(Res.string.plugin_force_update), style = MaterialTheme.typography.bodySmall, color = colors.error)
                        entry.openBlockReason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
                    }
                }
                item { PluginSourceLinks(selected?.sourceUrl ?: plugin.sourceUrl, selected) }
                item { Text(stringResource(Res.string.plugin_versions), style = MaterialTheme.typography.titleMedium) }
                items(entry.releases, key = { it.release.versionCode }) { option ->
                    val version = option.release
                    val isSelected = version == selected
                    val enabled = !busy && (option.blocked == null || version == installed?.release)
                    Surface(onClick = { release = version }, enabled = enabled,
                        modifier = Modifier.semantics { this.selected = isSelected }, shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) colors.primaryContainer else colors.surfaceContainerLow,
                        contentColor = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                        border = BorderStroke(EviloLayout.BorderWidth, if (isSelected) colors.primary else colors.outlineVariant)) {
                        Row(Modifier.fillMaxWidth().padding(EviloSpacing.Medium), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isSelected, onClick = null, enabled = enabled)
                            Column(Modifier.weight(1f).padding(start = EviloSpacing.Small), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                                Text(stringResource(if (version == installed?.release) Res.string.plugin_installed_version else Res.string.plugin_version,
                                    version.version), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(Res.string.plugin_version_requirements, version.minimumHostVersionCode,
                                    version.maximumHostVersionCode?.toString() ?: "*", version.protocol.major, version.protocol.minimumMinor), style = MaterialTheme.typography.bodySmall)
                                option.blocked?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
                            }
                        }
                    }
                }
                item { Text(stringResource(Res.string.plugin_safety), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(EviloSpacing.ExtraLarge), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                PluginInstallStatus(busy, blocked)
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                if (sameVersion) Text(stringResource(Res.string.plugin_already_installed), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                    if (sameVersion) Button(onClick = onOpen, enabled = !busy && canOpen) { Text(stringResource(Res.string.plugin_open)) }
                    else Button(onClick = { selected?.let(onReview) }, enabled = !busy && selected != null && blocked == null) {
                        Text(stringResource(if (installed == null) Res.string.plugin_install else Res.string.plugin_update))
                    }
                    if (!sameVersion && canOpen) OutlinedButton(onClick = onOpen, enabled = !busy) { Text(stringResource(Res.string.plugin_open)) }
                    if (installed != null) TextButton(onClick = onUninstall, enabled = !busy,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) { Text(stringResource(Res.string.plugin_uninstall)) }
                }
            }
        }
    }
}

@Composable
internal fun PluginPermissionDialog(entry: PluginEntry, release: PluginRelease, busy: Boolean, blocked: String?, error: String?,
    onDismiss: () -> Unit, onApprove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val update = entry.installation != null
    AlertDialog(onDismissRequest = onDismiss, containerColor = colors.surface, icon = { Icon(EviloIcons.Shield, null) },
        title = { Text(stringResource(if (update) Res.string.plugin_update_title else Res.string.plugin_install_title, entry.plugin.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                Column(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = EviloLayout.PermissionContentMaxHeight)
                    .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
                    Text(stringResource(Res.string.plugin_version, release.version), style = MaterialTheme.typography.titleSmall)
                    PluginSourceLinks(release.sourceUrl, release)
                    Text(stringResource(Res.string.plugin_safety))
                    if (entry.allowHttp) Text(stringResource(Res.string.plugin_http_warning), color = colors.error)
                    Text(stringResource(Res.string.plugin_permissions), style = MaterialTheme.typography.titleMedium)
                    NetworkPermissionText(stringResource(Res.string.plugin_api_origins), release.permissions.api)
                    NetworkPermissionText(stringResource(Res.string.plugin_media_origins), release.permissions.media)
                    if (release.permissions.storage) Text(stringResource(Res.string.plugin_storage))
                }
                PluginInstallStatus(busy, blocked)
                error?.takeIf { it != blocked }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error,
                    maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        },
        confirmButton = { Button(onClick = onApprove, enabled = !busy && blocked == null) {
            Text(stringResource(if (update) Res.string.plugin_accept_update else Res.string.plugin_accept_install))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.home_cancel)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginSourceLinks(sourceUrl: String, release: PluginRelease?) {
    val uri = LocalUriHandler.current
    var error by remember(sourceUrl, release) { mutableStateOf<String?>(null) }
    fun open(url: String) {
        try { uri.openUri(url) }
        catch (failure: Exception) { error = (failure.message ?: failure.toString()).take(2000) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
            OutlinedButton(onClick = { open(sourceUrl) }) { Text(stringResource(Res.string.plugin_source_code)) }
            release?.let { TextButton(onClick = { open(it.sourceArchiveUrl) }) { Text(stringResource(Res.string.plugin_source_archive)) } }
        }
        release?.let { Text(it.sourceRevision, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PluginInstallStatus(busy: Boolean, blocked: String?) {
    if (busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = EviloSpacing.Small))
        Text(stringResource(Res.string.plugin_wait_for_operation), style = MaterialTheme.typography.bodySmall)
    }
    blocked?.let { Text(it, Modifier.padding(vertical = EviloSpacing.Small), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error, maxLines = 4, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun NetworkPermissionText(title: String, access: HttpAccess) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (access.origins.isEmpty()) Text(stringResource(Res.string.plugin_no_network), style = MaterialTheme.typography.bodySmall)
            else {
                Text(access.origins.sorted().joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(Res.string.plugin_network_methods, access.methods.sorted().joinToString()), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text(stringResource(Res.string.plugin_network_paths, access.pathPrefixes.sorted().joinToString()), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (access.headers.isNotEmpty()) Text(stringResource(Res.string.plugin_network_headers, access.headers.sorted().joinToString()), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginSourcesSheet(state: PluginRepositoryState, busy: Boolean, error: String?, onDismiss: () -> Unit,
    onAdd: (String, Boolean) -> Unit, onEnabled: (String, Boolean) -> Unit, onRemove: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var url by remember { mutableStateOf("") }
    var allowHttp by remember { mutableStateOf(false) }
    var sourceCount by remember { mutableStateOf(state.subscriptions.size) }
    LaunchedEffect(state.subscriptions.size) {
        if (state.subscriptions.size > sourceCount) { url = ""; allowHttp = false }
        sourceCount = state.subscriptions.size
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), sheetMaxWidth = EviloLayout.SheetMaxWidth) {
        LazyColumn(Modifier.fillMaxWidth(), state = rememberLazyListState(),
            contentPadding = PaddingValues(start = EviloSpacing.ExtraLarge, end = EviloSpacing.ExtraLarge, bottom = EviloSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                    Text(stringResource(Res.string.source_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(Res.string.source_description), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            item { OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
                label = { Text(stringResource(Res.string.source_url)) }, leadingIcon = { Icon(EviloIcons.Link, null) },
                shape = MaterialTheme.shapes.medium, textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                    Row(Modifier.fillMaxWidth().toggleable(allowHttp, enabled = !busy, role = Role.Checkbox, onValueChange = { allowHttp = it }),
                        horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(allowHttp, onCheckedChange = null, enabled = !busy)
                        Text(stringResource(Res.string.source_http_consent), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    }
                    if (allowHttp || url.trim().startsWith("http://")) Text(stringResource(Res.string.plugin_http_warning), style = MaterialTheme.typography.bodySmall, color = colors.error)
                    error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = { onAdd(url.trim(), allowHttp) }, modifier = Modifier.fillMaxWidth(), enabled = !busy &&
                        (url.trim().startsWith("https://") || (allowHttp && url.trim().startsWith("http://")))) { Text(stringResource(Res.string.source_add)) }
                }
            }
            item { HorizontalDivider(color = colors.outlineVariant) }
            items(state.subscriptions, key = { it.id }) { source ->
                val snapshot = state.snapshots.firstOrNull { it.subscription.id == source.id }
                Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow, border = BorderStroke(EviloLayout.BorderWidth, colors.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                        Row(Modifier.fillMaxWidth().toggleable(source.enabled, enabled = !busy, role = Role.Switch,
                            onValueChange = { onEnabled(source.id, it) }), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                                Text(snapshot?.catalog?.name ?: source.url, style = MaterialTheme.typography.titleSmall)
                                if (source.id == OFFICIAL_SOURCE_ID) Text(stringResource(Res.string.source_official), style = MaterialTheme.typography.bodySmall, color = colors.primary)
                            }
                            Switch(source.enabled, onCheckedChange = null, enabled = !busy)
                        }
                        Text(source.url, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        if (source.allowHttp) Text(stringResource(Res.string.plugin_http_warning), style = MaterialTheme.typography.bodySmall, color = colors.error)
                        if (snapshot?.fromCache == true) Text(stringResource(Res.string.source_cached), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        snapshot?.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
                        if (source.id != OFFICIAL_SOURCE_ID) TextButton(onClick = { onRemove(source.id) }, enabled = !busy,
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) { Text(stringResource(Res.string.source_remove)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SourceRemovalDialog(busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(Res.string.source_remove_title)) }, text = { Text(stringResource(Res.string.source_remove_description)) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(Res.string.source_remove)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.home_cancel)) } })
}

@Composable
internal fun PluginUninstallDialog(name: String, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(Res.string.plugin_uninstall_title, name)) }, text = { Text(stringResource(Res.string.plugin_uninstall_description)) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(Res.string.plugin_uninstall)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.home_cancel)) } })
}

@Composable
internal fun PluginSafetyDialog(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(onDismissRequest = onDismiss, containerColor = colors.surface, icon = { Icon(EviloIcons.Shield, null) },
        title = { Text(stringResource(Res.string.more_safety)) }, text = {
            Column(Modifier.heightIn(max = EviloLayout.PermissionContentMaxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
                Text(stringResource(Res.string.plugin_safety))
                HorizontalDivider(color = colors.outlineVariant)
                Text(stringResource(Res.string.more_reader_scope), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.home_dismiss)) } })
}
