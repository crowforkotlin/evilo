package crow.evilo.feature.browse

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import crow.evilo.app.resources.*
import crow.evilo.plugin.catalog.BUNDLED_SOURCE_ID
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloMotion
import crow.evilo.ui.EviloSettingContent
import crow.evilo.ui.EviloSettingsGroup
import crow.evilo.ui.EviloSpacing
import org.jetbrains.compose.resources.stringResource

internal fun sourceName(state: PluginHomeState, id: String): String =
    state.catalog.sourceDisplayName(id)

private fun sourceDomain(url: String) = url.substringAfter("://").substringBefore('/').substringBefore('?')

@Composable
internal fun PluginSourcesScreen(state: PluginHomeState, dispatch: (PluginHomeIntent) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val padding = if (maxWidth >= EviloLayout.ExpandedBreakpoint) EviloLayout.WidePagePadding else EviloLayout.PagePadding
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(padding),
            verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
            item { Text(stringResource(Res.string.source_description), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.catalog.subscriptions, key = { it.id }) { source ->
                val toggleDescription = stringResource(Res.string.source_toggle_for, sourceName(state, source.id))
                Surface(onClick = { dispatch(PluginHomeIntent.ShowSourceCatalog(source.id)) },
                    shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(EviloLayout.BorderWidth, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().animateContentSize(EviloMotion.componentSpec()).padding(EviloSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                            Column(Modifier.weight(1f)) {
                                Text(sourceName(state, source.id), style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(sourceDomain(source.url), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Switch(source.enabled, onCheckedChange = { dispatch(PluginHomeIntent.EnableSource(source.id, it)) },
                                enabled = state.canMutate, modifier = Modifier.heightIn(min = EviloLayout.MinimumTouchTarget)
                                    .semantics { contentDescription = toggleDescription })
                        }
                        if (source.id == BUNDLED_SOURCE_ID) PluginBadge(stringResource(Res.string.source_bundled))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(Res.string.source_install_counts,
                                state.catalog.installed.count { it.sourceId == source.id },
                                state.catalog.snapshots.firstOrNull { it.subscription.id == source.id }?.catalog?.plugins?.size ?: 0),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { dispatch(PluginHomeIntent.ShowSourceSettings(source.id)) }) {
                                Icon(EviloIcons.Settings, stringResource(Res.string.source_settings))
                            }
                        }
                        SourceCheckStatus(state, source.id)
                        if (!source.enabled) Text(stringResource(Res.string.source_disabled), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { OutlinedButton(onClick = { dispatch(PluginHomeIntent.ShowAddSource) }) {
                Icon(EviloIcons.Add, null, Modifier.size(EviloLayout.InlineIconSize))
                Text(stringResource(Res.string.source_add_title), Modifier.padding(start = EviloSpacing.Small))
            } }
        }
    }
}

@Composable
internal fun AddPluginSourceScreen(state: PluginHomeState, dispatch: (PluginHomeIntent) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var allowHttp by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(EviloLayout.PagePadding),
        verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
        item { Text(stringResource(Res.string.source_description), style = MaterialTheme.typography.bodyMedium) }
        item { OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), enabled = state.canMutate, singleLine = true,
            label = { Text(stringResource(Res.string.source_url)) }, leadingIcon = { Icon(EviloIcons.Link, null) },
            shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)) }
        item {
            Row(Modifier.fillMaxWidth().toggleable(allowHttp, enabled = state.canMutate, role = Role.Checkbox,
                onValueChange = { allowHttp = it }), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(allowHttp, onCheckedChange = null, enabled = state.canMutate)
                Text(stringResource(Res.string.source_http_consent), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
            }
        }
        if (allowHttp || url.trim().startsWith("http://")) item {
            Text(stringResource(Res.string.plugin_http_warning), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
        }
        item { Button(onClick = { dispatch(PluginHomeIntent.AddSource(url.trim(), allowHttp)) }, enabled = state.canMutate &&
            (url.trim().startsWith("https://") || (allowHttp && url.trim().startsWith("http://")))) {
            Text(stringResource(Res.string.source_add))
        } }
    }
}

@Suppress("DEPRECATION")
@Composable
internal fun PluginSourceSettingsScreen(state: PluginHomeState, id: String, dispatch: (PluginHomeIntent) -> Unit) {
    val source = state.catalog.subscriptions.firstOrNull { it.id == id } ?: return
    val clipboard = LocalClipboardManager.current
    val snapshot = state.catalog.snapshots.firstOrNull { it.subscription.id == id }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(EviloLayout.PagePadding),
        verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
        item {
            EviloSettingsGroup(sourceName(state, id)) {
                Surface(Modifier.toggleable(source.enabled, enabled = state.canMutate, role = Role.Switch,
                    onValueChange = { dispatch(PluginHomeIntent.EnableSource(id, it)) }), color = androidx.compose.ui.graphics.Color.Transparent) {
                    EviloSettingContent(EviloIcons.Link, stringResource(if (source.enabled) Res.string.source_enabled else Res.string.source_disabled),
                        stringResource(Res.string.source_toggle_description)) { Switch(source.enabled, onCheckedChange = null, enabled = state.canMutate) }
                }
            }
        }
        item {
            EviloSettingsGroup(stringResource(Res.string.plugin_subscription_source)) {
                Column(Modifier.padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                    SelectionContainer { Text(source.url, style = MaterialTheme.typography.bodyMedium) }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(source.url)) }) { Text(stringResource(Res.string.source_copy_url)) }
                    if (source.id == BUNDLED_SOURCE_ID) Text(stringResource(Res.string.source_bundled), style = MaterialTheme.typography.bodySmall)
                    if (source.allowHttp) Text(stringResource(Res.string.plugin_http_warning), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            EviloSettingsGroup(stringResource(Res.string.source_check_updates)) {
                Column(Modifier.padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                    SourceCheckStatus(state, id, detailed = true)
                    snapshot?.let { Text(stringResource(Res.string.source_catalog_updated_at, it.catalog.updatedAt),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    OutlinedButton(onClick = { dispatch(PluginHomeIntent.RefreshSource(id)) }, enabled = source.enabled && !state.catalog.refreshing) {
                        Text(stringResource(Res.string.source_check_updates))
                    }
                }
            }
        }
        item { OutlinedButton(onClick = { dispatch(PluginHomeIntent.ShowSourceCatalog(id)) }) { Text(stringResource(Res.string.home_filter_all)) } }
        if (id != BUNDLED_SOURCE_ID) item {
            TextButton(onClick = { dispatch(PluginHomeIntent.RequestSourceRemoval(id)) }, enabled = state.canMutate,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(Res.string.source_remove))
            }
        }
    }
}

@Composable
internal fun SourceCheckStatus(state: PluginHomeState, id: String, detailed: Boolean = false) {
    val check = state.catalog.sourceChecks[id]
    val snapshot = state.catalog.snapshots.firstOrNull { it.subscription.id == id }
    if (check?.running == true) Text(stringResource(Res.string.source_checking), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary)
    if (check?.usingCache == true || snapshot?.fromCache == true) Text(stringResource(Res.string.source_cached),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    (check?.error ?: snapshot?.error)?.let { Text(it, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error, maxLines = if (detailed) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis) }
    if (detailed) Text(check?.lastSuccessAt?.let { stringResource(Res.string.source_checked_at, it) }
        ?: stringResource(Res.string.source_never_checked), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
