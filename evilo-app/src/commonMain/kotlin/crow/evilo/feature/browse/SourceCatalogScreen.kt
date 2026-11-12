package crow.evilo.feature.browse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import crow.evilo.app.EviloImages
import crow.evilo.app.resources.*
import crow.evilo.ui.EviloContentTransition
import crow.evilo.ui.EviloEmptyState
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloSearchField
import crow.evilo.ui.EviloSpacing
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SourceCatalogScreen(state: PluginHomeState, id: String, images: EviloImages, dispatch: (PluginHomeIntent) -> Unit) {
    EviloContentTransition(state, Modifier.fillMaxSize(), contentKey = { it.catalog.selectedKind to (it.sourceFilters[id] ?: CatalogFilter.ALL) },
        label = "source-catalog-filter") { displayed -> SourceCatalogContent(displayed, id, images, dispatch) }
}

@Composable
private fun SourceCatalogContent(state: PluginHomeState, id: String, images: EviloImages, dispatch: (PluginHomeIntent) -> Unit) {
    val source = state.catalog.subscriptions.firstOrNull { it.id == id } ?: return
    val projection = projectSourceCatalog(state, id)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val padding = if (maxWidth >= EviloLayout.ExpandedBreakpoint) EviloLayout.WidePagePadding else EviloLayout.PagePadding
        LazyVerticalGrid(columns = GridCells.Adaptive(EviloLayout.PluginCardMinWidth), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(padding), horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                    EviloSearchField(state.sourceQueries[id].orEmpty(), { dispatch(PluginHomeIntent.SearchSource(id, it)) },
                        stringResource(Res.string.browse_search_source), Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                        CatalogFilter.entries.forEach { filter ->
                            FilterChip(selected = (state.sourceFilters[id] ?: CatalogFilter.ALL) == filter,
                                onClick = { dispatch(PluginHomeIntent.FilterSource(id, filter)) },
                                label = { Text(stringResource(if (filter == CatalogFilter.ALL) Res.string.home_filter_all else Res.string.home_filter_installed)) })
                        }
                    }
                    SourceCheckStatus(state, id)
                    if (!source.enabled) {
                        Text(stringResource(Res.string.source_disabled_catalog), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { dispatch(PluginHomeIntent.EnableSource(id, true)) }, enabled = state.canMutate) {
                            Text(stringResource(Res.string.source_enable))
                        }
                    }
                    if (projection.hiddenNsfw > 0) {
                        Text(stringResource(Res.string.home_hidden_nsfw, projection.hiddenNsfw), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { dispatch(PluginHomeIntent.ShowSettings(SettingsSection.BROWSE)) }) { Text(stringResource(Res.string.settings_browse)) }
                    }
                    Text(stringResource(Res.string.home_result_count, projection.entries.size), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (projection.entries.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                EviloEmptyState(EviloIcons.Link, stringResource(Res.string.source_catalog_empty), stringResource(Res.string.source_catalog_empty_hint),
                    Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        dispatch(PluginHomeIntent.SearchSource(id, ""))
                        dispatch(PluginHomeIntent.FilterSource(id, CatalogFilter.ALL))
                    }) { Text(stringResource(Res.string.home_clear_filters)) }
                }
            }
            items(projection.entries, key = { it.key }) { entry ->
                PluginRow(entry, images, enabled = !state.changing, compact = false,
                    nsfw = entry.plugin.nsfw || state.catalog.isPluginNsfw(entry.plugin.id),
                    onOpen = { dispatch(PluginHomeIntent.ManagePlugin(entry.key)) },
                    onSettings = { dispatch(PluginHomeIntent.ManagePlugin(entry.key)) })
            }
        }
    }
}
