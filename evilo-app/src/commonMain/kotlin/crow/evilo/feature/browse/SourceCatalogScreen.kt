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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import crow.evilo.app.EviloImages
import crow.evilo.app.resources.*
import crow.evilo.plugin.catalog.CatalogSnapshot
import crow.evilo.plugin.catalog.SourceCheckState
import crow.evilo.ui.EviloContentTransition
import crow.evilo.ui.EviloEmptyState
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloLoadingState
import crow.evilo.ui.EviloMotion
import crow.evilo.ui.EviloRetryState
import crow.evilo.ui.EviloSearchField
import crow.evilo.ui.EviloSpacing
import crow.evilo.ui.LocalEviloPageActive
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SourceCatalogScreen(state: PluginHomeState, id: String, images: EviloImages, dispatch: (PluginHomeIntent) -> Unit) {
    EviloContentTransition(state, Modifier.fillMaxSize(), contentKey = { it.catalog.selectedKind to (it.sourceFilters[id] ?: CatalogFilter.ALL) },
        label = "source-catalog-filter") { displayed -> SourceCatalogContent(displayed, id, images, dispatch) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SourceCatalogContent(state: PluginHomeState, id: String, images: EviloImages, dispatch: (PluginHomeIntent) -> Unit) {
    val source = state.catalog.subscriptions.firstOrNull { it.id == id } ?: return
    val check = state.catalog.sourceChecks[id]
    val snapshot = state.catalog.snapshots.firstOrNull { it.subscription.id == id }
    val error = sourceCheckError(check, snapshot)
    val running = check?.running == true
    val pullState = rememberPullToRefreshState()
    var pullRefreshing by remember(id) { mutableStateOf(false) }
    LaunchedEffect(state.catalog.refreshing, running) {
        if (!state.catalog.refreshing && !running) pullRefreshing = false
    }
    val showPullProgress = pullRefreshing && (running || state.catalog.refreshing) && error == null
    // Computed per input change, not per recomposition: the transition animates this screen.
    val projection = remember(state.entries, state.catalog, state.sourceFilters[id], state.sourceQueries[id], id) {
        projectSourceCatalog(state, id)
    }
    val presentation = sourceCatalogPresentation(projection.entries.isNotEmpty(), check, snapshot,
        source.enabled && state.catalog.refreshing)
    PullToRefreshBox(isRefreshing = showPullProgress, onRefresh = {
        if (source.enabled && !state.catalog.refreshing) {
            pullRefreshing = true
            dispatch(PluginHomeIntent.RefreshSource(id))
        }
    }, state = pullState, modifier = Modifier.fillMaxSize(),
        enabled = source.enabled && !state.catalog.refreshing && LocalEviloPageActive.current,
        indicator = {
            if (error == null && (showPullProgress || pullState.distanceFraction > 0f)) {
                PullToRefreshDefaults.LoadingIndicator(pullState, showPullProgress, Modifier.align(Alignment.TopCenter))
            }
        }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val padding = if (maxWidth >= EviloLayout.ExpandedBreakpoint) EviloLayout.WidePagePadding else EviloLayout.PagePadding
            LazyVerticalGrid(columns = GridCells.Adaptive(EviloLayout.PluginCardMinWidth), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(padding), horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
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
                        if (error != null && projection.entries.isNotEmpty() && source.enabled) TextButton(
                            onClick = { dispatch(PluginHomeIntent.RefreshSource(id)) }, enabled = !state.catalog.refreshing) {
                            Text(stringResource(Res.string.source_retry))
                        }
                        if (!source.enabled) {
                            Text(stringResource(Res.string.source_disabled_catalog), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { dispatch(PluginHomeIntent.EnableSource(id, true)) }, modifier = Modifier.fillMaxWidth(), enabled = state.canMutate) {
                                Text(stringResource(Res.string.source_enable))
                            }
                        }
                        if (projection.hiddenNsfw > 0) {
                            Text(stringResource(Res.string.home_hidden_nsfw, projection.hiddenNsfw), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { dispatch(PluginHomeIntent.ShowSettings(SettingsSection.BROWSE)) }) { Text(stringResource(Res.string.settings_browse)) }
                        }
                        if (presentation == SourceCatalogPresentation.CONTENT || presentation == SourceCatalogPresentation.EMPTY) {
                            Text(stringResource(Res.string.home_result_count, projection.entries.size), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                when (presentation) {
                    SourceCatalogPresentation.LOADING -> if (!showPullProgress) item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        EviloLoadingState(stringResource(Res.string.source_checking))
                    }
                    SourceCatalogPresentation.FAILURE -> item(key = "failure", span = { GridItemSpan(maxLineSpan) }) {
                        if (source.enabled) EviloRetryState(
                            stringResource(Res.string.source_check_failed), stringResource(Res.string.source_check_retry_hint),
                            onRetry = { dispatch(PluginHomeIntent.RefreshSource(id)) })
                        else EviloEmptyState(EviloIcons.Link, stringResource(Res.string.source_check_failed),
                            stringResource(Res.string.source_check_retry_hint), Modifier.fillMaxWidth()) {}
                    }
                    SourceCatalogPresentation.EMPTY -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EviloEmptyState(EviloIcons.Link, stringResource(Res.string.source_catalog_empty), stringResource(Res.string.source_catalog_empty_hint),
                            Modifier.fillMaxWidth()) {
                            TextButton(onClick = {
                                dispatch(PluginHomeIntent.SearchSource(id, ""))
                                dispatch(PluginHomeIntent.FilterSource(id, CatalogFilter.ALL))
                            }) { Text(stringResource(Res.string.home_clear_filters)) }
                        }
                    }
                    SourceCatalogPresentation.CONTENT -> Unit
                }
                items(projection.entries, key = { it.key }) { entry ->
                    PluginRow(entry, images, enabled = !state.changing, compact = false,
                        nsfw = entry.plugin.nsfw || state.catalog.isPluginNsfw(entry.plugin.id),
                        modifier = Modifier.animateItem(fadeInSpec = EviloMotion.componentSpec(),
                            placementSpec = EviloMotion.spatialSpec(), fadeOutSpec = EviloMotion.exitSpec()),
                        onOpen = { dispatch(PluginHomeIntent.ManagePlugin(entry.key)) },
                        onSettings = { dispatch(PluginHomeIntent.ManagePlugin(entry.key)) })
                }
            }
        }
    }
}

internal fun sourceCheckError(check: SourceCheckState?, snapshot: CatalogSnapshot?): String? = check?.error ?: snapshot?.error

internal enum class SourceCatalogPresentation { CONTENT, LOADING, FAILURE, EMPTY }

internal fun sourceCatalogPresentation(hasEntries: Boolean, check: SourceCheckState?, snapshot: CatalogSnapshot?, refreshing: Boolean): SourceCatalogPresentation = when {
    hasEntries -> SourceCatalogPresentation.CONTENT
    sourceCheckError(check, snapshot) != null -> SourceCatalogPresentation.FAILURE
    check?.running == true || (refreshing && snapshot == null) -> SourceCatalogPresentation.LOADING
    else -> SourceCatalogPresentation.EMPTY
}
