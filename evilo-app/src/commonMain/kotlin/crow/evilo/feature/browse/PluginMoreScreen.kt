package crow.evilo.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crow.evilo.plugin.catalog.CatalogListing
import crow.evilo.plugin.catalog.InstalledPlugin
import crow.evilo.plugin.catalog.PluginCatalogRepository
import crow.evilo.plugin.catalog.PluginRepositoryState
import crow.evilo.ui.EviloLayout
import crow.evilo.app.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PluginMoreScreen(repository: PluginCatalogRepository, state: PluginRepositoryState, icons: PluginIconLoader,
    onOpen: (InstalledPlugin) -> Unit, onPlugin: (CatalogListing) -> Unit, onSources: () -> Unit,
    enabled: Boolean, onNsfw: (Boolean) -> Unit, onUninstall: (InstalledPlugin) -> Unit) {
    val installed = state.installed.filter {
        state.selectedKind in it.release.kinds && (state.showNsfw || !(state.catalogPluginByKey[it.key] ?: it.plugin).nsfw)
    }
    var remove by remember { mutableStateOf<InstalledPlugin?>(null) }
    LazyVerticalGrid(columns = GridCells.Adaptive(360.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(EviloLayout.PagePadding), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.more_heading), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(Res.string.more_description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.more_preferences), style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.more_show_nsfw), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(Res.string.more_nsfw_description), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(state.showNsfw, onNsfw, enabled = enabled)
                    }
                    OutlinedButton(onClick = onSources) { Text(stringResource(Res.string.home_manage_sources)) }
                }
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.more_safety), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(Res.string.plugin_safety), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(Res.string.more_reader_scope), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(Res.string.more_about, repository.host.appVersion, repository.host.protocolMajor, repository.host.protocolMinor),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(Res.string.more_installed), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.more_installed_count, installed.size, mediaKindLabel(state.selectedKind)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (installed.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.more_no_installed), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.more_install_hint), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onSources) { Text(stringResource(Res.string.home_manage_sources)) }
                }
            }
        }
        items(installed, key = { it.key }) { installation ->
            val plugin = state.catalogPluginByKey[installation.key] ?: installation.plugin
            val listing = state.listings.firstOrNull { it.key == installation.key }
            val blocked = repository.installationBlockReason(installation)
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        PluginAvatar(plugin.name, plugin.iconUrl, installation.allowHttp, icons)
                        Column(Modifier.weight(1f)) {
                            Text(plugin.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(Res.string.plugin_version, installation.release.version), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (blocked != null) Text(blocked, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpen(installation) }, enabled = enabled && blocked == null) { Text(stringResource(Res.string.plugin_open)) }
                        if (listing != null) TextButton(onClick = { onPlugin(listing) }, enabled = enabled) { Text(stringResource(Res.string.plugin_versions)) }
                        TextButton(onClick = { remove = installation }, enabled = enabled) { Text(stringResource(Res.string.plugin_uninstall)) }
                    }
                }
            }
        }
    }
    remove?.let { installation -> AlertDialog(onDismissRequest = { remove = null },
        title = { Text(stringResource(Res.string.plugin_uninstall_title, installation.plugin.name)) },
        text = { Text(stringResource(Res.string.plugin_uninstall_description)) },
        confirmButton = { TextButton(onClick = { remove = null; onUninstall(installation) }) { Text(stringResource(Res.string.plugin_uninstall)) } },
        dismissButton = { TextButton(onClick = { remove = null }) { Text(stringResource(Res.string.home_cancel)) } }) }
}
