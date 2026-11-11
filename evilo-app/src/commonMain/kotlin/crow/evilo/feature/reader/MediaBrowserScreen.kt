package crow.evilo.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import crow.evilo.app.EviloImages
import crow.evilo.app.SessionImages
import crow.evilo.architecture.closeResources
import crow.evilo.model.MediaItem
import crow.evilo.model.MediaKind
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloAsyncImage
import crow.evilo.ui.EviloElevation
import crow.evilo.ui.EviloEmptyState
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloSearchField
import crow.evilo.ui.EviloSpacing
import crow.evilo.app.resources.*
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaBrowserRoute(session: MediaSourceSession, kind: MediaKind, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val controller = koinInject<MediaBrowserController>(parameters = { parametersOf(session, kind) })
    val imageLoader = koinInject<EviloImages>()
    val images = remember(session, imageLoader) { imageLoader.sessionImages(session) }
    val state by controller.state.collectAsState()
    DisposableEffect(images) { onDispose { images.close() } }
    LaunchedEffect(controller, images) {
        try {
            controller.dispatch(MediaBrowserIntent.Search(""))
            awaitCancellation()
        } finally {
            closeResources({ images.close() }, { controller.close() })
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val pagePadding = if (maxWidth >= EviloLayout.ExpandedBreakpoint) EviloLayout.WidePagePadding else EviloLayout.PagePadding
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                        Text(session.source.descriptor.name, style = MaterialTheme.typography.titleLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(Res.string.source_plugin_version, session.source.descriptor.version,
                            session.source.descriptor.protocol.major), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (state.page == MediaBrowserPage.CATALOG) onClose() else controller.dispatch(MediaBrowserIntent.Back) }) {
                        Icon(EviloIcons.Back, stringResource(Res.string.source_back))
                    }
                },
                actions = { IconButton(onClick = onClose) { Icon(EviloIcons.Close, stringResource(Res.string.source_disconnect)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { MediaError(it, { controller.dispatch(MediaBrowserIntent.Retry) }, Modifier.padding(horizontal = pagePadding, vertical = EviloSpacing.Small)) }
            when (state.page) {
                MediaBrowserPage.CATALOG -> MediaCatalog(state, images,
                    onSearch = { controller.dispatch(MediaBrowserIntent.Search(it)) },
                    onMore = { controller.dispatch(MediaBrowserIntent.LoadMore) },
                    onItem = { controller.dispatch(MediaBrowserIntent.OpenItem(it)) }, pagePadding = pagePadding)
                MediaBrowserPage.DETAILS -> MediaDetails(state, images, { controller.dispatch(MediaBrowserIntent.OpenChapter(it)) }, pagePadding)
                MediaBrowserPage.READER -> MediaReader(state, session, images, { controller.dispatch(MediaBrowserIntent.OpenChapter(it)) }, pagePadding)
            }
        }
    }
}

@Composable
private fun MediaCatalog(state: MediaBrowserState, images: SessionImages, onSearch: (String) -> Unit,
    onMore: () -> Unit, onItem: (MediaItem) -> Unit, pagePadding: Dp) {
    var query by remember { mutableStateOf(state.query) }
    val colors = MaterialTheme.colorScheme
    LazyVerticalGrid(columns = GridCells.Adaptive(if (state.kind in setOf(MediaKind.FILM, MediaKind.ANIME))
        EviloLayout.VideoCardMinWidth else EviloLayout.MediaCardMinWidth),
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(pagePadding),
        horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
                    EviloSearchField(query, { query = it }, stringResource(Res.string.source_search_hint), Modifier.weight(1f),
                        onSearch = { onSearch(query) })
                    FilledIconButton(onClick = { onSearch(query) }) {
                        Icon(EviloIcons.Search, stringResource(Res.string.source_search))
                    }
                }
                Text(stringResource(Res.string.source_result_count, state.items.size), style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant)
            }
        }
        if (state.items.isEmpty() && !state.busy && state.error == null) item(span = { GridItemSpan(maxLineSpan) }) {
            EviloEmptyState(EviloIcons.kind(state.kind), stringResource(Res.string.source_no_results),
                stringResource(Res.string.source_no_results_description), Modifier.fillMaxWidth()) {
                Button(onClick = { onSearch(query) }) { Text(stringResource(Res.string.source_retry)) }
            }
        }
        items(state.items, key = { "${it.id.pluginId}:${it.id.kind}:${it.id.value}" }) { item ->
            Card(onClick = { onItem(item) }, shape = MaterialTheme.shapes.medium,
                border = BorderStroke(EviloLayout.BorderWidth, colors.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = EviloElevation.Flat),
                colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                MediaImage(item.coverUrl, emptyMap(), images, item.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Column(Modifier.padding(EviloSpacing.Medium), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.authors.isNotEmpty()) Text(item.authors.joinToString(", "), style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (state.items.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.hasNextPage) OutlinedButton(onClick = onMore, enabled = !state.busy) {
                    Text(stringResource(Res.string.source_load_more))
                } else Text(stringResource(Res.string.source_results_end), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MediaDetails(state: MediaBrowserState, images: SessionImages, onChapter: (Int) -> Unit, pagePadding: Dp) {
    val item = state.selectedItem ?: return
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = EviloLayout.ReaderMaxWidth).fillMaxSize(), contentPadding = PaddingValues(pagePadding),
            verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
                    MediaImage(item.coverUrl, emptyMap(), images, item.title,
                        Modifier.width(EviloLayout.DetailCoverWidth).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.medium))
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
            if (state.chapters.isEmpty() && !state.busy && state.error == null) item {
                Text(stringResource(Res.string.source_no_chapters), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            itemsIndexed(state.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                Surface(onClick = { onChapter(index) }, shape = MaterialTheme.shapes.medium, color = colors.surface,
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
private fun MediaReader(state: MediaBrowserState, session: MediaSourceSession, images: SessionImages, onChapter: (Int) -> Unit, pagePadding: Dp) {
    val index = state.chapterIndex ?: return
    val chapter = state.chapters.getOrNull(index) ?: return
    val content = state.content
    val listState = remember(chapter.id) { LazyListState() }
    val uriHandler = LocalUriHandler.current
    var playbackError by remember(chapter.id) { mutableStateOf<String?>(null) }
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
            LazyColumn(state = listState, modifier = Modifier.widthIn(max = EviloLayout.ReaderMaxWidth).fillMaxSize(),
                contentPadding = PaddingValues(bottom = EviloSpacing.ExtraLarge), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                content?.text?.let { text -> item { Text(text, Modifier.padding(pagePadding), style = MaterialTheme.typography.bodyLarge) } }
                if (!content?.streams.isNullOrEmpty()) {
                    item { Text(stringResource(Res.string.source_external_player_description), Modifier.padding(pagePadding), style = MaterialTheme.typography.bodyMedium) }
                    itemsIndexed(content?.streams.orEmpty()) { _, stream ->
                        OutlinedButton(onClick = {
                            try {
                                uriHandler.openUri(session.externalStreamUrl(stream))
                            } catch (failure: Exception) {
                                playbackError = (failure.message ?: failure.toString()).take(2000)
                            }
                        }, modifier = Modifier.padding(horizontal = pagePadding)) { Text(stream.title) }
                    }
                }
                playbackError?.let { error -> item { Text(error, Modifier.padding(pagePadding), color = MaterialTheme.colorScheme.error) } }
                itemsIndexed(content?.images.orEmpty(), key = { _, page -> page.index }) { _, page ->
                    Column {
                        Text(stringResource(Res.string.source_image_position, page.index + 1), Modifier.padding(horizontal = pagePadding, vertical = EviloSpacing.ExtraSmall),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MediaImage(page.imageUrl, page.headers, images, chapter.title, Modifier.fillMaxWidth(), naturalSize = true)
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
private fun MediaError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
            Text(stringResource(Res.string.source_error_title), style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.source_retry)) }
        }
    }
}

@Composable
internal fun MediaImage(url: String?, headers: Map<String, String>, loader: SessionImages, description: String,
    modifier: Modifier = Modifier, naturalSize: Boolean = false) {
    var attempt by remember(url, headers, loader) { mutableStateOf(0) }
    val request = remember(url, headers, loader, attempt, naturalSize) { loader.request(url, headers, attempt, naturalSize) }
    EviloAsyncImage(request, loader.loader, description, modifier, naturalSize, onRetry = { attempt++ })
}
