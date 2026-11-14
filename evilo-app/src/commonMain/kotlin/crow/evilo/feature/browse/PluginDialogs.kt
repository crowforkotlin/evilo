package crow.evilo.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import crow.evilo.plugin.catalog.fingerprint
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import crow.evilo.app.resources.*
import crow.evilo.plugin.api.HttpAccess
import crow.evilo.plugin.catalog.PluginRelease
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloSpacing
import crow.evilo.ui.LocalEviloToastState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PluginPermissionDialog(entry: PluginEntry, release: PluginRelease, busy: Boolean, blocked: String?,
    openAfterInstall: Boolean, sharedPluginData: Boolean, onDismiss: () -> Unit, onApprove: (Boolean) -> Unit) {
    val installed = entry.installation
    val update = installed != null && release.versionCode > installed.release.versionCode
    val official = entry.releases.firstOrNull { it.release == release }?.officialSigner == true
    var publisherApproved by remember(entry.key, release) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        icon = { Icon(EviloIcons.Shield, null) },
        title = { Text(stringResource(when {
            update -> Res.string.plugin_update_title
            installed != null -> Res.string.plugin_review_title
            else -> Res.string.plugin_install_title
        }, entry.plugin.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                Column(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = EviloLayout.PermissionContentMaxHeight)
                    .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
                    Text(stringResource(Res.string.plugin_version, release.version), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(Res.string.plugin_subscription_source), style = MaterialTheme.typography.titleSmall)
                    SelectionContainer { Text(entry.listing?.snapshot?.subscription?.url ?: installed?.sourceUrl.orEmpty(),
                        style = MaterialTheme.typography.bodySmall) }
                    PluginSourceLinks(release.sourceUrl, release)
                    PluginSigningText(release, official)
                    if (!official) Row {
                        Checkbox(publisherApproved, onCheckedChange = { publisherApproved = it })
                        Text(stringResource(Res.string.plugin_publisher_accept), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(stringResource(Res.string.plugin_safety), style = MaterialTheme.typography.bodyMedium)
                    if (entry.allowHttp) Text(stringResource(Res.string.plugin_http_warning), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(Res.string.plugin_permissions), style = MaterialTheme.typography.titleMedium)
                    NetworkPermissionText(stringResource(Res.string.plugin_api_origins), release.permissions.api)
                    NetworkPermissionText(stringResource(Res.string.plugin_media_origins), release.permissions.media)
                    if (release.permissions.storage) Text(stringResource(Res.string.plugin_storage), style = MaterialTheme.typography.bodySmall)
                    if (sharedPluginData) Text(stringResource(Res.string.plugin_shared_data), style = MaterialTheme.typography.bodySmall)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                blocked?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = { onApprove(publisherApproved) }, enabled = !busy && blocked == null && (official || publisherApproved)) {
            Text(stringResource(when {
                openAfterInstall && update -> Res.string.plugin_accept_update_browse
                openAfterInstall -> Res.string.plugin_accept_install_browse
                update -> Res.string.plugin_accept_update
                installed != null && release.versionCode < installed.release.versionCode -> Res.string.plugin_accept_downgrade
                installed != null -> Res.string.plugin_accept_reinstall
                else -> Res.string.plugin_accept_install
            }))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.home_cancel)) } })
}

@Composable
internal fun PluginSigningText(release: PluginRelease, official: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
        Text(stringResource(if (official) Res.string.plugin_signer_official else Res.string.plugin_signer_third_party),
            style = MaterialTheme.typography.titleSmall)
        release.signing?.let { signing -> SelectionContainer {
            Text(stringResource(Res.string.plugin_signer_fingerprint, signing.fingerprint()), style = MaterialTheme.typography.bodySmall)
        } }
        release.manifestSha256?.let { digest -> SelectionContainer {
            Text(stringResource(Res.string.plugin_manifest_hash, digest), style = MaterialTheme.typography.bodySmall)
        } }
        if (!official) Text(stringResource(Res.string.plugin_publisher_warning), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PluginSourceLinks(sourceUrl: String, release: PluginRelease?) {
    val uri = LocalUriHandler.current
    val toasts = LocalEviloToastState.current
    val errorTitle = stringResource(Res.string.home_error)
    fun open(url: String) {
        try { uri.openUri(url) }
        catch (failure: Exception) { toasts.show((failure.message ?: failure.toString()).take(500), title = errorTitle, error = true) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
            OutlinedButton(onClick = { open(sourceUrl) }) { Text(stringResource(Res.string.plugin_source_code)) }
            release?.let { OutlinedButton(onClick = { open(it.sourceArchiveUrl) }) { Text(stringResource(Res.string.plugin_source_archive)) } }
        }
        release?.let {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                    Text(stringResource(Res.string.plugin_source_revision, it.sourceRevision), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(Res.string.plugin_source_hash, it.sourceArchiveSha256), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun NetworkPermissionText(title: String, access: HttpAccess) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (access.origins.isEmpty()) Text(stringResource(Res.string.plugin_no_network), style = MaterialTheme.typography.bodySmall)
            else {
                Text(access.origins.sorted().joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(Res.string.plugin_network_methods, access.methods.sorted().joinToString()), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(Res.string.plugin_network_paths, access.pathPrefixes.sorted().joinToString()), style = MaterialTheme.typography.bodySmall)
                if (access.headers.isNotEmpty()) Text(stringResource(Res.string.plugin_network_headers, access.headers.sorted().joinToString()),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun SourceRemovalDialog(name: String, installations: Int, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(Res.string.source_remove_title)) },
        text = { Text(stringResource(Res.string.source_remove_count_description, name, installations)) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(Res.string.source_remove)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.home_cancel)) } })
}

@Composable
internal fun PluginUninstallDialog(name: String, source: String, otherInstallations: Int, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(Res.string.plugin_uninstall_title, name)) },
        text = { Text(stringResource(Res.string.plugin_uninstall_source_description, source, otherInstallations)) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(Res.string.plugin_uninstall)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.home_cancel)) } })
}
