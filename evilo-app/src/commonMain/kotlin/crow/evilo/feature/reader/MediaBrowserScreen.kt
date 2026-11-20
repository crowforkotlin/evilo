package crow.evilo.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import crow.evilo.app.EviloImages
import crow.evilo.app.SessionImages
import crow.evilo.app.LocalAppDiagnostics
import crow.evilo.app.PluginLoadTiming
import crow.evilo.feature.browse.PluginLoadDiagnosticsDialog
import crow.evilo.architecture.closeResources
import crow.evilo.model.MediaItem
import crow.evilo.model.MediaKind
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloAsyncImage
import crow.evilo.ui.EviloElevation
import crow.evilo.ui.EviloEmptyState
import crow.evilo.ui.EviloLoadingState
import crow.evilo.ui.EviloMotion
import crow.evilo.ui.EviloContentTransition
import crow.evilo.ui.LocalEviloAnimatedVisibilityScope
import crow.evilo.ui.EviloRetryState
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloPageTransition
import crow.evilo.ui.EviloPredictiveBackHandler
import crow.evilo.ui.rememberEviloBackTransition
import crow.evilo.ui.eviloTransitionInput
import crow.evilo.ui.EviloSearchField
import crow.evilo.ui.EviloSpacing
import crow.evilo.ui.LocalEviloPageActive
import crow.evilo.ui.LocalEviloToastState
import crow.evilo.app.resources.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MediaBrowserRoute(session: MediaSourceSession?, kind: MediaKind, onClose: () -> Unit, modifier: Modifier = Modifier,
    onReopen: () -> Unit = onClose, onPluginDetails: (() -> Unit)? = null, loadTimings: List<PluginLoadTiming> = emptyList(),
    pluginName: String = "", pluginVersion: String = "", connecting: Boolean = false, connectionError: String? = null) {
    val imageLoader = koinInject<EviloImages>()
    val controller = if (session != null) {
        val parameters = remember(session, kind) { parametersOf(session, kind) }
        koinInject<MediaBrowserController>(parameters = { parameters })
    } else null
    val images = remember(session, imageLoader) { session?.let(imageLoader::sessionImages) }
    val stateFlow = remember(controller, kind) { controller?.state ?: MutableStateFlow(MediaBrowserState(kind, busy = connecting)) }
    val observed by key(controller, kind) { stateFlow.collectAsState() }
    val rawState = mediaRouteState(observed, kind, sessionReady = session != null, connecting = connecting, connectionError = connectionError)
    val state = rememberMediaLoadingPresentation(rawState, controller ?: kind)
    val connectedFlow = remember(images) { images?.connected ?: MutableStateFlow(false) }
    // A replacement scope must start from its own current value, not the previous
    // nullable scope's false retained by collectAsState across a flow replacement.
    val connected by key(images) { connectedFlow.collectAsState() }
    val savedPages = key(session) { rememberSaveableStateHolder() }
    val backTransition = rememberEviloBackTransition(state, ::mediaPageKey)
    val focus = remember { FocusRequester() }
    val pageActive = LocalEviloPageActive.current
    val toasts = LocalEviloToastState.current
    var errorToastId by remember { mutableStateOf<Long?>(null) }
    var showLoadDiagnostics by remember(session) { mutableStateOf(false) }
    if (showLoadDiagnostics) PluginLoadDiagnosticsDialog(loadTimings) { showLoadDiagnostics = false }
    val errorTitle = mediaFailureTitle(state.page)
    val retryLabel = stringResource(Res.string.source_retry)
    val back: () -> Unit = { if (!connected || state.page == MediaBrowserPage.CATALOG) onClose() else controller?.dispatch(MediaBrowserIntent.Back) }
    // Catalog/disconnected Back belongs to the home stack, which can preview its real destination.
    EviloPredictiveBackHandler((pageActive || backTransition.previewing) && connected && state.page != MediaBrowserPage.CATALOG && !showLoadDiagnostics,
        backTransition, state, state.backDestination(), navigationKey = mediaPageKey(state), onBack = back,
        sharedElements = state.page == MediaBrowserPage.DETAILS && state.selectedItem?.let { selected ->
            !selected.coverUrl.isNullOrBlank() && state.items.any { it.id == selected.id && it.coverUrl == selected.coverUrl }
        } == true)
    LaunchedEffect(controller, state.page, pageActive, backTransition.previewing) {
        if (pageActive && !backTransition.previewing) focus.requestFocus()
    }
    val hasDisplayedContent = when (state.page) {
        MediaBrowserPage.CATALOG -> state.items.isNotEmpty()
        MediaBrowserPage.DETAILS -> state.chapters.isNotEmpty()
        MediaBrowserPage.READER -> state.content != null
    }
    LaunchedEffect(state.error, state.page, pageActive, connected, hasDisplayedContent) {
        errorToastId?.let { toasts.dismiss(it, notify = false) }
        errorToastId = state.error?.takeIf { pageActive && connected && hasDisplayedContent }?.let { error ->
            toasts.show(error, title = errorTitle, error = true, actionLabel = retryLabel,
                onAction = { controller?.dispatch(MediaBrowserIntent.Retry) })
        }
    }
    DisposableEffect(toasts) { onDispose { errorToastId?.let { toasts.dismiss(it, notify = false) } } }
    DisposableEffect(images) { onDispose { images?.close() } }
    LaunchedEffect(controller, images) {
        if (controller == null || images == null) return@LaunchedEffect
        try {
            controller.dispatch(MediaBrowserIntent.Search(""))
            awaitCancellation()
        } finally {
            closeResources({ images.close() }, { controller.close() })
        }
    }
    SharedTransitionLayout(modifier) {
    val coverTransitions = this
    EviloPageTransition(state, contentKey = ::mediaPageKey, depth = { it.page.ordinal },
        label = "media-page", backState = backTransition) { displayed ->
    val state = displayed
    BoxWithConstraints(Modifier.fillMaxSize().eviloTransitionInput(!backTransition.previewing).onPreviewKeyEvent {
        if (pageActive && it.key == Key.Escape && it.type == KeyEventType.KeyUp) { back(); true } else false
    }.focusRequester(focus).focusable()) {
        val pagePadding = if (maxWidth >= EviloLayout.ExpandedBreakpoint) EviloLayout.WidePagePadding else EviloLayout.PagePadding
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                        Text(pluginName.ifBlank { session?.source?.descriptor?.name.orEmpty() }, style = MaterialTheme.typography.titleLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(Res.string.source_plugin_version, pluginVersion.ifBlank { session?.source?.descriptor?.version.orEmpty() }),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(EviloIcons.Back, stringResource(Res.string.source_back))
                    }
                },
                actions = {
                    if (LocalAppDiagnostics.current && loadTimings.isNotEmpty()) IconButton(onClick = { showLoadDiagnostics = true }) {
                        Icon(EviloIcons.Info, stringResource(Res.string.plugin_debug_timings))
                    }
                    IconButton(onClick = onClose) { Icon(EviloIcons.Close,
                        stringResource(if (connecting) Res.string.home_cancel else Res.string.source_disconnect)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
            if (session == null) {
                PendingPluginCatalog(connecting, connectionError,
                    pagePadding, onReopen, onPluginDetails, Modifier.weight(1f))
            } else if (!connected) {
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                    EviloRetryState(stringResource(Res.string.plugin_session_closed), stringResource(Res.string.image_error_session_hint),
                        onReopen, Modifier.padding(pagePadding), retryLabel = stringResource(Res.string.plugin_reopen))
                }
            } else if (images != null && controller != null) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                        savedPages.SaveableStateProvider(mediaPageKey(displayed)) {
                            val retry: () -> Unit = { controller.dispatch(MediaBrowserIntent.Retry) }
                            when (displayed.page) {
                                MediaBrowserPage.CATALOG -> MediaCatalog(displayed, images,
                                    onSearch = { controller.dispatch(MediaBrowserIntent.Search(it)) },
                                    onMore = { controller.dispatch(MediaBrowserIntent.LoadMore) },
                                    onItem = { controller.dispatch(MediaBrowserIntent.OpenItem(it)) }, pagePadding = pagePadding,
                                    onPluginDetails = onPluginDetails, onRetry = retry,
                                    coverTransitions = coverTransitions, shareCovers = !backTransition.previewing || backTransition.seekSharedElements)
                                MediaBrowserPage.DETAILS -> MediaDetails(displayed, images,
                                    { controller.dispatch(MediaBrowserIntent.OpenChapter(it)) }, pagePadding, onPluginDetails, retry,
                                    coverTransitions = coverTransitions, shareCovers = !backTransition.previewing || backTransition.seekSharedElements)
                                MediaBrowserPage.READER -> MediaReader(displayed, session, images,
                                    { controller.dispatch(MediaBrowserIntent.OpenChapter(it)) }, pagePadding, onPluginDetails, retry)
                            }
                        }
                }
            }
        }
    }
    }
    }
}

@Composable
private fun PendingPluginCatalog(loading: Boolean, error: String?, pagePadding: Dp,
    onRetry: () -> Unit, onPluginDetails: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = pagePadding)) {
        MediaCatalogSearch("", {}, {}, enabled = false)
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            if (loading && error == null) EviloLoadingState(stringResource(Res.string.source_loading_content))
            else EviloRetryState(stringResource(Res.string.source_catalog_failed),
                error ?: stringResource(Res.string.plugin_session_closed), onRetry,
                secondaryAction = {
                    onPluginDetails?.let { details -> TextButton(onClick = details) { Text(stringResource(Res.string.plugin_details)) } }
                })
        }
    }
}

@Composable
private fun MediaCatalogSearch(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
        EviloSearchField(query, onQuery, stringResource(Res.string.source_search_hint), Modifier.weight(1f), enabled = enabled,
            onSearch = onSearch)
        FilledIconButton(onClick = onSearch, enabled = enabled) { Icon(EviloIcons.Search, stringResource(Res.string.source_search)) }
    }
}

internal enum class MediaLoadPhase { LOADING, ERROR, EMPTY, CONTENT }
internal fun mediaLoadPhase(busy: Boolean, error: String?, hasContent: Boolean): MediaLoadPhase = when {
    hasContent -> MediaLoadPhase.CONTENT
    busy -> MediaLoadPhase.LOADING
    error != null -> MediaLoadPhase.ERROR
    else -> MediaLoadPhase.EMPTY
}

@Composable
private fun MediaLoadStateTransition(state: MediaBrowserState, hasContent: Boolean, label: String,
    modifier: Modifier = Modifier, content: @Composable (MediaBrowserState) -> Unit) {
    EviloContentTransition(state, contentKey = { mediaLoadPhase(it.busy, it.error, hasContent) },
        label = label, modifier = modifier.fillMaxWidth()) { displayed ->
        if (displayed.busy) {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }
            AnimatedVisibility(visible, enter = fadeIn(EviloMotion.componentSpec()), exit = fadeOut(EviloMotion.exitSpec())) {
                EviloLoadingState(stringResource(Res.string.source_loading_content))
            }
        } else content(displayed)
    }
}

internal enum class CatalogLoadingPlacement { NONE, CENTER, FOOTER, PULL }

internal fun catalogLoadingPlacement(busy: Boolean, hasItems: Boolean, refreshing: Boolean,
    error: String? = null): CatalogLoadingPlacement = when {
    !busy || error != null -> CatalogLoadingPlacement.NONE
    refreshing -> CatalogLoadingPlacement.PULL
    hasItems -> CatalogLoadingPlacement.FOOTER
    else -> CatalogLoadingPlacement.CENTER
}

/** Package preparation and the first API request share the same catalog destination. */
internal fun mediaRouteState(observed: MediaBrowserState, kind: MediaKind, sessionReady: Boolean,
    connecting: Boolean, connectionError: String?): MediaBrowserState =
    if (!sessionReady) MediaBrowserState(kind, busy = connecting, error = connectionError)
    else if (observed.page == MediaBrowserPage.CATALOG && observed.searchPage == 0 && observed.error == null && !observed.busy)
        observed.copy(busy = true)
    else observed

internal fun mediaPageKey(state: MediaBrowserState): String = when (state.page) {
    MediaBrowserPage.CATALOG -> "catalog"
    MediaBrowserPage.DETAILS -> "details:${state.selectedItem?.id}"
    MediaBrowserPage.READER -> "reader:${state.chapters.getOrNull(state.chapterIndex ?: -1)?.id}"
}

@Composable
private fun mediaFailureTitle(page: MediaBrowserPage): String = stringResource(when (page) {
    MediaBrowserPage.CATALOG -> Res.string.source_catalog_failed
    MediaBrowserPage.DETAILS -> Res.string.source_details_failed
    MediaBrowserPage.READER -> Res.string.source_content_failed
})

internal const val MediaMinimumLoadingMillis = 400L

internal fun mediaMinimumLoadingRemaining(elapsedMillis: Long): Long =
    (MediaMinimumLoadingMillis - elapsedMillis).coerceIn(0L, MediaMinimumLoadingMillis)

/** Delay presentation only, never the controller operation or its cancellation. */
@Composable
private fun rememberMediaLoadingPresentation(observed: MediaBrowserState, owner: Any): MediaBrowserState {
    val destination = mediaPageKey(observed)
    var presented by remember(owner) { mutableStateOf(observed) }
    var started by remember(owner) { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }
    LaunchedEffect(owner, destination, observed.busy) {
        if (observed.busy) {
            started = TimeSource.Monotonic.markNow()
            presented = observed
        } else {
            if (mediaPageKey(presented) == destination && presented.busy) {
                delay(mediaMinimumLoadingRemaining(started?.elapsedNow()?.inWholeMilliseconds ?: MediaMinimumLoadingMillis))
            }
            started = null
            presented = observed.copy(busy = false)
        }
    }
    // While loading, freeze only the content response. Header/page identity may
    // update, but a short success/failure cannot replace the indicator mid-cycle.
    return if (presented.busy && mediaPageKey(presented) == destination) {
        observed.copy(busy = true, error = null, items = presented.items, chapters = presented.chapters, content = presented.content)
    } else observed
}

/** Fade a newly populated region once; restoration and scrolling do not replay it. */
@Composable
private fun mediaContentEntrance(hasContent: Boolean): Modifier {
    val alpha = remember { Animatable(if (hasContent) 1f else 0f) }
    LaunchedEffect(hasContent) {
        if (hasContent) alpha.animateTo(1f, EviloMotion.componentSpec()) else alpha.snapTo(0f)
    }
    return Modifier.graphicsLayer { this.alpha = alpha.value }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaCatalog(state: MediaBrowserState, images: SessionImages, onSearch: (String) -> Unit,
    onMore: () -> Unit, onItem: (MediaItem) -> Unit, pagePadding: Dp, onPluginDetails: (() -> Unit)?, onRetry: () -> Unit,
    coverTransitions: SharedTransitionScope, shareCovers: Boolean) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    val gridState = rememberLazyGridState()
    val colors = MaterialTheme.colorScheme
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.busy, state.error) { if (!state.busy) refreshing = false }
    val loading = catalogLoadingPlacement(state.busy, state.items.isNotEmpty(), refreshing, state.error)
    val contentEntrance = mediaContentEntrance(state.items.isNotEmpty())
    PullToRefreshBox(isRefreshing = loading == CatalogLoadingPlacement.PULL,
        onRefresh = { if (!state.busy) { query = state.query; refreshing = true; onSearch(state.query) } },
        state = pullState, modifier = Modifier.fillMaxSize(), enabled = !state.busy && LocalEviloPageActive.current,
        indicator = {
            if (loading == CatalogLoadingPlacement.PULL ||
                (!state.busy && !refreshing && !pullState.isAnimating && pullState.distanceFraction > 0.01f)) {
                PullToRefreshDefaults.LoadingIndicator(pullState, loading == CatalogLoadingPlacement.PULL, Modifier.align(Alignment.TopCenter))
            }
        }) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = pagePadding), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                MediaCatalogSearch(query, { query = it }, { onSearch(query) }, enabled = !state.busy)
                if (state.items.isNotEmpty()) Text(stringResource(Res.string.source_result_count, state.items.size),
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            if (state.items.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                    EviloContentTransition(state, contentKey = { mediaLoadPhase(it.busy, it.error, it.items.isNotEmpty()) },
                        label = "catalog-load-state") { displayed ->
                        when {
                            displayed.busy && loading == CatalogLoadingPlacement.PULL -> Unit
                            displayed.busy -> EviloLoadingState(stringResource(Res.string.source_loading_content))
                            displayed.error != null -> MediaLoadFailure(displayed, onRetry)
                            else -> EviloEmptyState(EviloIcons.kind(displayed.kind), stringResource(Res.string.source_no_results),
                                stringResource(Res.string.source_no_results_description)) {
                                Button(onClick = { onSearch(query) }) { Text(stringResource(Res.string.source_reload)) }
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Adaptive(if (state.kind in setOf(MediaKind.FILM, MediaKind.ANIME))
                    EviloLayout.VideoCardMinWidth else EviloLayout.MediaCardMinWidth),
                    state = gridState, modifier = Modifier.weight(1f).fillMaxWidth().then(contentEntrance), contentPadding = PaddingValues(pagePadding),
                    horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
                    items(state.items, key = { "${it.id.pluginId}:${it.id.kind}:${it.id.value}" }) { item ->
                        Card(onClick = { onItem(item) }, shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.animateItem(fadeInSpec = EviloMotion.componentSpec(),
                                placementSpec = EviloMotion.spatialSpec(), fadeOutSpec = EviloMotion.exitSpec()),
                            border = BorderStroke(EviloLayout.BorderWidth, colors.outlineVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = EviloElevation.Flat),
                            colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                            MediaCover(item, images, coverTransitions, shareCovers,
                                Modifier.fillMaxWidth().aspectRatio(2f / 3f), onPluginDetails)
                            Column(Modifier.padding(EviloSpacing.Medium), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (item.authors.isNotEmpty()) Text(item.authors.joinToString(", "), style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            when {
                                state.error != null -> MediaLoadFailure(state, onRetry)
                                loading == CatalogLoadingPlacement.FOOTER -> LoadingIndicator(
                                    Modifier.size(EviloLayout.InlineLoadingIndicatorSize), color = colors.primary)
                                loading == CatalogLoadingPlacement.PULL -> Unit
                                state.hasNextPage -> OutlinedButton(onClick = onMore) { Text(stringResource(Res.string.source_load_more)) }
                                else -> Text(stringResource(Res.string.source_results_end), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaDetails(state: MediaBrowserState, images: SessionImages, onChapter: (Int) -> Unit, pagePadding: Dp,
    onPluginDetails: (() -> Unit)?, onRetry: () -> Unit, coverTransitions: SharedTransitionScope, shareCovers: Boolean) {
    val item = state.selectedItem ?: return
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = EviloLayout.ReaderMaxWidth).fillMaxSize(), contentPadding = PaddingValues(pagePadding),
            verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
                    MediaCover(item, images, coverTransitions, shareCovers,
                        Modifier.width(EviloLayout.DetailCoverWidth).aspectRatio(2f / 3f), onPluginDetails)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                        Text(item.title, style = MaterialTheme.typography.titleLarge)
                        if (item.authors.isNotEmpty()) Text(item.authors.joinToString(", "), style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant)
                        Text(item.id.value, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            item.description?.takeIf(String::isNotBlank)?.let { description -> item { Text(description, style = MaterialTheme.typography.bodyMedium) } }
            item { Text(stringResource(if (state.kind in setOf(MediaKind.ANIME, MediaKind.FILM)) Res.string.source_episodes
                else Res.string.source_chapters, state.chapters.size), style = MaterialTheme.typography.titleMedium) }
            if (state.chapters.isEmpty()) item(key = "chapter-status") {
                MediaLoadStateTransition(state, hasContent = false, label = "chapter-load-state",
                    modifier = Modifier.animateItem(fadeInSpec = EviloMotion.componentSpec(),
                        placementSpec = EviloMotion.spatialSpec(), fadeOutSpec = EviloMotion.exitSpec())) { displayed ->
                    if (displayed.error != null) MediaLoadFailure(displayed, onRetry)
                    else EviloEmptyState(EviloIcons.Book, stringResource(Res.string.source_no_chapters),
                        stringResource(Res.string.source_load_failed_hint)) {
                        OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.source_reload)) }
                    }
                }
            }
            itemsIndexed(state.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                Surface(onClick = { onChapter(index) }, shape = MaterialTheme.shapes.medium, color = colors.surface,
                    modifier = Modifier.animateItem(fadeInSpec = EviloMotion.componentSpec(),
                        placementSpec = EviloMotion.spatialSpec(), fadeOutSpec = EviloMotion.exitSpec()),
                    border = BorderStroke(EviloLayout.BorderWidth, colors.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = EviloLayout.MinimumTouchTarget).padding(EviloSpacing.Large),
                        horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = MaterialTheme.shapes.small, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                            Text((index + 1).toString().padStart(2, '0'),
                                Modifier.padding(horizontal = EviloSpacing.Small, vertical = EviloSpacing.ExtraSmall), style = MaterialTheme.typography.labelMedium)
                        }
                        Text(chapter.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Icon(EviloIcons.ChevronRight, null, tint = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaReader(state: MediaBrowserState, session: MediaSourceSession, images: SessionImages, onChapter: (Int) -> Unit, pagePadding: Dp, onPluginDetails: (() -> Unit)?, onRetry: () -> Unit) {
    val index = state.chapterIndex ?: return
    val chapter = state.chapters.getOrNull(index) ?: return
    val content = state.content
    val listState = rememberLazyListState()
    val contentEntrance = mediaContentEntrance(content != null)
    val uriHandler = LocalUriHandler.current
    val toasts = LocalEviloToastState.current
    val errorTitle = stringResource(Res.string.home_error)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(Modifier.widthIn(max = EviloLayout.ReaderMaxWidth).fillMaxWidth().padding(horizontal = pagePadding, vertical = EviloSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onChapter(index - 1) }, enabled = index > 0 && !state.busy) {
                    Icon(EviloIcons.Back, stringResource(Res.string.source_previous_chapter))
                }
                Text(chapter.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onChapter(index + 1) }, enabled = index + 1 < state.chapters.size && !state.busy) {
                    Icon(EviloIcons.ChevronRight, stringResource(Res.string.source_next_chapter))
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(state = listState, modifier = Modifier.widthIn(max = EviloLayout.ReaderMaxWidth).fillMaxSize()
                .then(if (content != null) contentEntrance else Modifier),
                contentPadding = PaddingValues(bottom = EviloSpacing.ExtraLarge), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                if (content == null) item(key = "reader-status") {
                    MediaLoadStateTransition(state, hasContent = false, label = "reader-load-state") { displayed ->
                        if (displayed.error != null) MediaLoadFailure(displayed, onRetry, Modifier.padding(horizontal = pagePadding))
                    }
                }
                content?.text?.let { text -> item { Text(text, Modifier.padding(pagePadding), style = MaterialTheme.typography.bodyLarge) } }
                if (!content?.streams.isNullOrEmpty()) {
                    item { Text(stringResource(Res.string.source_external_player_description), Modifier.padding(pagePadding), style = MaterialTheme.typography.bodyMedium) }
                    itemsIndexed(content?.streams.orEmpty()) { _, stream ->
                        OutlinedButton(onClick = {
                            try {
                                uriHandler.openUri(session.externalStreamUrl(stream))
                            } catch (failure: Exception) {
                                toasts.show((failure.message ?: failure.toString()).take(2000), title = errorTitle, error = true)
                            }
                        }, modifier = Modifier.padding(horizontal = pagePadding)) { Text(stream.title) }
                    }
                }
                itemsIndexed(content?.images.orEmpty(), key = { _, page -> page.index }) { _, page ->
                    Column {
                        Text(stringResource(Res.string.source_image_position, page.index + 1), Modifier.padding(horizontal = pagePadding, vertical = EviloSpacing.ExtraSmall),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MediaImage(page.imageUrl, page.headers, images, stringResource(Res.string.image_page_description, chapter.title, page.index + 1),
                            Modifier.fillMaxWidth(), naturalSize = true, pageNumber = page.index + 1, onPluginDetails = onPluginDetails)
                    }
                }
                if (content != null && content.text.isNullOrEmpty() && content.images.isEmpty() && content.streams.isEmpty()) {
                    item { Text(stringResource(Res.string.source_empty_content), Modifier.padding(pagePadding)) }
                }
            }
        }
    }
}

@Composable
private fun MediaLoadFailure(state: MediaBrowserState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EviloRetryState(mediaFailureTitle(state.page), stringResource(if (state.page == MediaBrowserPage.CATALOG && state.items.isEmpty())
        Res.string.source_catalog_retry_hint else Res.string.source_load_failed_hint), onRetry, modifier.fillMaxWidth())
}

/** Covers include the scope and URL: changed artwork must not match an old image. */
internal data class MediaCoverKey(val scope: Any, val mediaId: Any, val url: String)

@Composable
private fun MediaCover(item: MediaItem, images: SessionImages, transitions: SharedTransitionScope,
    enabled: Boolean, modifier: Modifier, onPluginDetails: (() -> Unit)?) {
    val visibility = LocalEviloAnimatedVisibilityScope.current
    val sharingEnabled = rememberUpdatedState(enabled)
    val config = remember {
        object : SharedTransitionScope.SharedContentConfig {
            override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
                get() = sharingEnabled.value
            override val shouldKeepEnabledForOngoingAnimation: Boolean get() = false
        }
    }
    val shape = MaterialTheme.shapes.medium
    val shared = with(transitions) {
        if (visibility != null && !item.coverUrl.isNullOrBlank()) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(MediaCoverKey(images, item.id, item.coverUrl.orEmpty()), config),
                animatedVisibilityScope = visibility,
                boundsTransform = { _, _ -> EviloMotion.spatialSpec() },
                enter = fadeIn(EviloMotion.componentSpec()),
                exit = fadeOut(EviloMotion.componentSpec()),
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                clipInOverlayDuringTransition = OverlayClip(shape),
                zIndexInOverlay = 1f,
            )
        } else Modifier
    }
    // Keep sizing outside the shared modifier and clip inside it, including the
    // overlay. Both endpoints use 2:3 bounds; placeholders/errors may differ.
    MediaImage(item.coverUrl, emptyMap(), images, item.title, modifier.then(shared).clip(shape),
        onPluginDetails = onPluginDetails)
}

@Composable
internal fun MediaImage(url: String?, headers: Map<String, String>, loader: SessionImages, description: String,
    modifier: Modifier = Modifier, naturalSize: Boolean = false, pageNumber: Int? = null, onPluginDetails: (() -> Unit)? = null) {
    var attempt by remember(url, headers, loader) { mutableStateOf(0) }
    val request = remember(url, headers, loader, attempt, naturalSize) { loader.request(url, headers, attempt, naturalSize) }
    EviloAsyncImage(request, loader.loader, description, modifier, naturalSize, onRetry = { attempt++ },
        address = url, pageNumber = pageNumber, onPluginDetails = onPluginDetails)
}
