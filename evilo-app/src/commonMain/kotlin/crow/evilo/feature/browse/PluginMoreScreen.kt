package crow.evilo.feature.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import crow.evilo.app.EviloImages
import crow.evilo.app.resources.*
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.ui.EviloEmptyState
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloSectionHeading
import crow.evilo.ui.EviloSettingContent
import crow.evilo.ui.EviloSpacing
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PluginMoreScreen(state: PluginHomeState, images: EviloImages, host: HostDescriptor, dispatch: (PluginHomeIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val installed = state.entries.values.filter { entry ->
        val installation = entry.installation
        installation != null && state.catalog.selectedKind in installation.release.kinds && (state.catalog.showNsfw || !entry.plugin.nsfw)
    }
    val onBrowse = { dispatch(PluginHomeIntent.Navigate(HomeDestination.BROWSE)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val padding = if (maxWidth >= EviloLayout.ExpandedBreakpoint) EviloLayout.WidePagePadding else EviloLayout.PagePadding
        val settingsColumns = if (maxWidth - padding * 2 >= EviloLayout.SettingsCardMinWidth * 2 + EviloSpacing.Large) 2 else 1
        LazyVerticalGrid(columns = GridCells.Adaptive(EviloLayout.PluginCardMinWidth), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = padding, end = padding, bottom = padding),
            horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = settingsColumns,
                    horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraLarge)) {
                    SettingsGroup(stringResource(Res.string.more_preferences), Modifier.weight(1f)) {
                        Surface(Modifier.toggleable(state.catalog.showNsfw, enabled = !state.busy, role = Role.Switch,
                            onValueChange = { dispatch(PluginHomeIntent.ShowNsfw(it)) }), color = Color.Transparent) {
                            EviloSettingContent(EviloIcons.Tune, stringResource(Res.string.more_show_nsfw), stringResource(Res.string.more_nsfw_description)) {
                                Switch(state.catalog.showNsfw, onCheckedChange = null, enabled = !state.busy)
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = EviloSpacing.Large), color = colors.outlineVariant)
                        Surface(onClick = { dispatch(PluginHomeIntent.ShowSources) }, color = Color.Transparent) {
                            EviloSettingContent(EviloIcons.Link, stringResource(Res.string.home_manage_sources),
                                stringResource(Res.string.more_source_count, state.catalog.subscriptions.count { it.enabled })) {
                                Icon(EviloIcons.ChevronRight, null, tint = colors.onSurfaceVariant)
                            }
                        }
                    }
                    SettingsGroup(stringResource(Res.string.more_appearance), Modifier.weight(1f)) {
                        EviloSettingContent(EviloIcons.Palette, stringResource(Res.string.more_system_theme), stringResource(Res.string.more_system_theme_description))
                        HorizontalDivider(Modifier.padding(horizontal = EviloSpacing.Large), color = colors.outlineVariant)
                        EviloSettingContent(EviloIcons.Novel, stringResource(Res.string.more_font), stringResource(Res.string.more_font_description))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
                    EviloSectionHeading(stringResource(Res.string.more_installed),
                        stringResource(Res.string.more_installed_count, installed.size, mediaKindLabel(state.catalog.selectedKind)), Modifier.weight(1f))
                    TextButton(onClick = onBrowse) { Text(stringResource(Res.string.home_browse)) }
                }
            }
            if (installed.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                EviloEmptyState(EviloIcons.kind(state.catalog.selectedKind), stringResource(Res.string.more_no_installed),
                    stringResource(Res.string.more_install_hint), Modifier.fillMaxWidth()) {
                    Button(onClick = onBrowse) { Text(stringResource(Res.string.home_browse)) }
                }
            }
            items(installed, key = { it.key }) { entry -> PluginRow(entry, images, !state.busy,
                onOpen = { dispatch(PluginHomeIntent.OpenPlugin(entry.key)) }, onSettings = { dispatch(PluginHomeIntent.ManagePlugin(entry.key)) }) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(onClick = { dispatch(PluginHomeIntent.ShowSafety) }, shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow) {
                    EviloSettingContent(EviloIcons.Shield, stringResource(Res.string.more_safety), stringResource(Res.string.more_safety_description)) {
                        Icon(EviloIcons.ChevronRight, null, tint = colors.onSurfaceVariant)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(Res.string.more_about, host.appVersion, host.protocolMajor, host.protocolMinor),
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, shape = MaterialTheme.shapes.large, color = colors.surface, border = BorderStroke(EviloLayout.BorderWidth, colors.outlineVariant)) {
        Column {
            Text(title, Modifier.padding(start = EviloSpacing.Large, end = EviloSpacing.Large, top = EviloSpacing.Large),
                style = MaterialTheme.typography.labelLarge, color = colors.primary)
            content()
        }
    }
}
