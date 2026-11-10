package crow.evilo.feature.browse

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import crow.evilo.plugin.catalog.CatalogListing
import crow.evilo.plugin.catalog.CatalogSubscription
import crow.evilo.plugin.catalog.InstalledPlugin
import crow.evilo.plugin.catalog.OFFICIAL_SOURCE_ID
import crow.evilo.plugin.catalog.PluginCatalogRepository
import crow.evilo.plugin.catalog.PluginRelease
import crow.evilo.plugin.catalog.PluginRepositoryState
import crow.evilo.plugin.api.HttpAccess
import crow.evilo.app.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PluginDetailsSheet(
    listing: CatalogListing,
    repository: PluginCatalogRepository,
    icons: PluginIconLoader,
    busy: Boolean,
    onDismiss: () -> Unit,
    onInstall: (PluginRelease) -> Unit,
    onOpen: (InstalledPlugin) -> Unit,
) {
    val state by repository.state.collectAsState()
    val current = state.listings.firstOrNull { it.key == listing.key } ?: listing
    val plugin = current.plugin
    val installed = state.installed.firstOrNull { it.key == listing.key }
    var release by remember(listing.key) { mutableStateOf(repository.recommendedRelease(current)) }
    var requestConsent by remember { mutableStateOf(false) }
    var externalError by remember { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current
    LaunchedEffect(plugin.availableVersions) {
        if (release !in plugin.availableVersions) release = repository.recommendedRelease(current)
    }
    val selected = release
    val blocked = if (selected == null) stringResource(Res.string.plugin_no_compatible_version)
        else repository.releaseBlockReason(current, selected)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PluginAvatar(plugin.name, plugin.iconUrl, current.snapshot.subscription.allowHttp, icons)
                    Column(Modifier.weight(1f)) {
                        Text(plugin.name, style = MaterialTheme.typography.headlineSmall)
                        Text(plugin.authors.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Text(plugin.description) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PluginBadge(stringResource(if (current.officiallyCertified) Res.string.plugin_official else Res.string.plugin_community))
                    Text(plugin.kinds.map { mediaKindLabel(it) }.joinToString(" / ") + " | " + plugin.languages.joinToString())
                    Text(stringResource(Res.string.plugin_created, plugin.createdAt.substringBefore('T')), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(Res.string.plugin_updated, plugin.updatedAt.substringBefore('T')), style = MaterialTheme.typography.bodySmall)
                    if (plugin.forceUpdate) Text(stringResource(Res.string.plugin_force_update), color = MaterialTheme.colorScheme.error)
                }
            }
            item {
                OutlinedButton(onClick = {
                    try { uri.openUri(selected?.sourceUrl ?: plugin.sourceUrl) }
                    catch (failure: Exception) { externalError = failure.message ?: failure.toString() }
                }) { Text(stringResource(Res.string.plugin_source_code)) }
                selected?.let { version ->
                    TextButton(onClick = {
                        try { uri.openUri(version.sourceArchiveUrl) }
                        catch (failure: Exception) { externalError = failure.message ?: failure.toString() }
                    }) { Text(stringResource(Res.string.plugin_source_archive)) }
                    Text(version.sourceRevision, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
                externalError?.let { Text(it.take(2000), color = MaterialTheme.colorScheme.error) }
            }
            item { Text(stringResource(Res.string.plugin_versions), style = MaterialTheme.typography.titleLarge) }
            items(plugin.availableVersions.sortedByDescending { it.versionCode }, key = { it.versionCode }) { version ->
                val reason = repository.releaseBlockReason(current, version)
                Surface(onClick = { release = version }, enabled = !busy && reason == null, shape = MaterialTheme.shapes.medium,
                    color = if (version == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = version == selected, onClick = { release = version }, enabled = !busy && reason == null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(Res.string.plugin_version, version.version), style = MaterialTheme.typography.titleSmall)
                            Text("App ${version.minimumHostVersionCode}..${version.maximumHostVersionCode ?: "*"} | Protocol ${version.protocol.major}.${version.protocol.minimumMinor}",
                                style = MaterialTheme.typography.labelSmall)
                            if (reason != null) Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item {
                Text(stringResource(Res.string.plugin_safety), style = MaterialTheme.typography.bodySmall)
                if (blocked != null) Text(blocked, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { requestConsent = true }, enabled = !busy && selected != null && blocked == null) {
                        Text(stringResource(if (installed != null && selected?.versionCode != installed.release.versionCode) Res.string.plugin_update else Res.string.plugin_install))
                    }
                    if (installed != null) OutlinedButton(onClick = { onOpen(installed) }, enabled = !busy && repository.installationBlockReason(installed) == null) {
                        Text(stringResource(Res.string.plugin_open))
                    }
                }
            }
        }
    }
    if (requestConsent && selected != null) PluginPermissionDialog(current, selected, onDismiss = { requestConsent = false }, onApprove = {
        requestConsent = false
        onInstall(selected)
    })
}

@Composable
private fun PluginPermissionDialog(listing: CatalogListing, release: PluginRelease, onDismiss: () -> Unit, onApprove: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(Res.string.plugin_install_title, listing.plugin.name)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(stringResource(Res.string.plugin_version, release.version), style = MaterialTheme.typography.titleSmall) }
                item { Text(stringResource(Res.string.plugin_safety)) }
                if (listing.snapshot.subscription.allowHttp) item { Text(stringResource(Res.string.plugin_http_warning), color = MaterialTheme.colorScheme.error) }
                item { Text(stringResource(Res.string.plugin_permissions), style = MaterialTheme.typography.titleMedium) }
                item { NetworkPermissionText(stringResource(Res.string.plugin_api_origins), release.permissions.api) }
                item { NetworkPermissionText(stringResource(Res.string.plugin_media_origins), release.permissions.media) }
                if (release.permissions.storage) item { Text(stringResource(Res.string.plugin_storage)) }
            }
        }, confirmButton = { Button(onClick = onApprove) { Text(stringResource(Res.string.plugin_accept_install)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.home_cancel)) } })
}

@Composable
private fun NetworkPermissionText(title: String, access: HttpAccess) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (access.origins.isEmpty()) Text(stringResource(Res.string.plugin_no_network), style = MaterialTheme.typography.bodySmall)
        else {
            Text(access.origins.sorted().joinToString("\n"), style = MaterialTheme.typography.bodySmall)
            Text(access.methods.sorted().joinToString() + " | " + access.pathPrefixes.sorted().joinToString(), style = MaterialTheme.typography.labelSmall)
            Text(access.headers.sorted().joinToString(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginSourcesSheet(state: PluginRepositoryState, busy: Boolean, error: String?, onDismiss: () -> Unit,
    onAdd: (String, Boolean) -> Unit, onEnabled: (String, Boolean) -> Unit, onRemove: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var allowHttp by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<CatalogSubscription?>(null) }
    var sourceCount by remember { mutableStateOf(state.subscriptions.size) }
    LaunchedEffect(state.subscriptions.size) {
        if (state.subscriptions.size > sourceCount) { url = ""; allowHttp = false }
        sourceCount = state.subscriptions.size
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(stringResource(Res.string.source_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.source_description), Modifier.padding(top = 8.dp))
            }
            item { OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
                label = { Text(stringResource(Res.string.source_url)) }) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(allowHttp, { allowHttp = it }, enabled = !busy)
                    Text(stringResource(Res.string.source_http_consent), style = MaterialTheme.typography.bodySmall)
                }
                if (allowHttp || url.trim().startsWith("http://")) Text(stringResource(Res.string.plugin_http_warning), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = { onAdd(url.trim(), allowHttp) }, enabled = !busy &&
                    (url.trim().startsWith("https://") || (allowHttp && url.trim().startsWith("http://")))) { Text(stringResource(Res.string.source_add)) }
            }
            items(state.subscriptions, key = { it.id }) { source ->
                val snapshot = state.snapshots.firstOrNull { it.subscription.id == source.id }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(snapshot?.catalog?.name ?: source.url, style = MaterialTheme.typography.titleMedium)
                                if (source.id == OFFICIAL_SOURCE_ID) Text(stringResource(Res.string.source_official), style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(source.enabled, { onEnabled(source.id, it) }, enabled = !busy)
                        }
                        Text(source.url, style = MaterialTheme.typography.bodySmall)
                        if (source.allowHttp) Text(stringResource(Res.string.plugin_http_warning), style = MaterialTheme.typography.bodySmall)
                        if (snapshot?.fromCache == true) Text(stringResource(Res.string.source_cached), style = MaterialTheme.typography.labelSmall)
                        snapshot?.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        if (source.id != OFFICIAL_SOURCE_ID) TextButton(onClick = { remove = source }, enabled = !busy) { Text(stringResource(Res.string.source_remove)) }
                    }
                }
            }
        }
    }
    remove?.let { source -> AlertDialog(onDismissRequest = { remove = null }, title = { Text(stringResource(Res.string.source_remove_title)) },
        text = { Text(stringResource(Res.string.source_remove_description)) },
        confirmButton = { TextButton(onClick = { remove = null; onRemove(source.id) }) { Text(stringResource(Res.string.source_remove)) } },
        dismissButton = { TextButton(onClick = { remove = null }) { Text(stringResource(Res.string.home_cancel)) } }) }
}
