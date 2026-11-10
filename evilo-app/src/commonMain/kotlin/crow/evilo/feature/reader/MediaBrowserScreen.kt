package crow.evilo.feature.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crow.evilo.model.MediaItem
import crow.evilo.model.MediaKind
import crow.evilo.ui.EviloLayout
import crow.evilo.app.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
fun MediaBrowserRoute(session: MediaSourceSession, kind: MediaKind, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val controller = remember(session, kind) { MediaBrowserController(session, kind, scope) }
    val images = remember(session) { session.imageLoader() }
    val state by controller.state.collectAsState()
    LaunchedEffect(controller) {
        try {
            controller.search("")
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { controller.close() }
        }
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (state.page == MediaBrowserPage.CATALOG) onClose() else controller.back() }) {
                Text(stringResource(Res.string.source_back))
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(session.source.descriptor.name, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(Res.string.source_plugin_version, session.source.descriptor.version,
                    session.source.descriptor.protocol.major), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onClose) { Text(stringResource(Res.string.source_disconnect)) }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { MediaError(it, controller::retry, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        when (state.page) {
            MediaBrowserPage.CATALOG -> MediaCatalog(state, images, controller::search, controller::loadMore, controller::openItem)
            MediaBrowserPage.DETAILS -> MediaDetails(state, images, controller::openChapter)
            MediaBrowserPage.READER -> MediaReader(state, session, images, controller::openChapter)
        }
    }
}

@Composable
private fun MediaCatalog(state: MediaBrowserState, images: SourceImageLoader, onSearch: (String) -> Unit,
    onMore: () -> Unit, onItem: (MediaItem) -> Unit) {
    var query by remember { mutableStateOf(state.query) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true,
                label = { Text(stringResource(Res.string.source_search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }))
            Button(onClick = { onSearch(query) }) { Text(stringResource(Res.string.source_search)) }
        }
        Text(stringResource(Res.string.source_result_count, state.items.size),
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.labelLarge)
        if (state.items.isEmpty()) {
            if (!state.busy && state.error == null) Text(stringResource(Res.string.source_no_results), Modifier.padding(20.dp))
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(if (state.kind == MediaKind.FILM || state.kind == MediaKind.ANIME) 190.dp else 150.dp),
                modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(state.items, key = { "${it.id.pluginId}:${it.id.kind}:${it.id.value}" }) { item ->
                    Card(onClick = { onItem(item) }) {
                        MediaImage(item.coverUrl, emptyMap(), images, item.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (item.authors.isNotEmpty()) Text(item.authors.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.hasNextPage) OutlinedButton(onClick = onMore, enabled = !state.busy) {
                            Text(stringResource(Res.string.source_load_more))
                        } else Text(stringResource(Res.string.source_results_end), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaDetails(state: MediaBrowserState, images: SourceImageLoader, onChapter: (Int) -> Unit) {
    val item = state.selectedItem ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MediaImage(item.coverUrl, emptyMap(), images, item.title, Modifier.width(100.dp).aspectRatio(2f / 3f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.title, style = MaterialTheme.typography.headlineSmall)
                    if (item.authors.isNotEmpty()) Text(item.authors.joinToString(", "))
                    Text(item.id.value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item.description?.takeIf(String::isNotBlank)?.let { description -> item { Text(description) } }
        item { Text(stringResource(if (state.kind in setOf(MediaKind.ANIME, MediaKind.FILM)) Res.string.source_episodes
            else Res.string.source_chapters, state.chapters.size), style = MaterialTheme.typography.titleLarge) }
        if (state.chapters.isEmpty() && !state.busy && state.error == null) item { Text(stringResource(Res.string.source_no_chapters)) }
        itemsIndexed(state.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
            Surface(onClick = { onChapter(index) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text((index + 1).toString().padStart(2, '0'), style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
                    Text(chapter.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(Res.string.source_read), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun MediaReader(state: MediaBrowserState, session: MediaSourceSession, images: SourceImageLoader, onChapter: (Int) -> Unit) {
    val index = state.chapterIndex ?: return
    val chapter = state.chapters.getOrNull(index) ?: return
    val content = state.content
    val listState = remember(chapter.id) { LazyListState() }
    val uriHandler = LocalUriHandler.current
    var playbackError by remember(chapter.id) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChapter(index - 1) }, enabled = index > 0 && !state.busy) { Text(stringResource(Res.string.source_previous_chapter)) }
            Text(chapter.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { onChapter(index + 1) }, enabled = index + 1 < state.chapters.size && !state.busy) {
                Text(stringResource(Res.string.source_next_chapter))
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(state = listState, modifier = Modifier.widthIn(max = EviloLayout.ReaderMaxWidth).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content?.text?.let { text -> item { Text(text, Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge) } }
                if (!content?.streams.isNullOrEmpty()) {
                    item { Text(stringResource(Res.string.source_external_player_description), Modifier.padding(20.dp)) }
                    itemsIndexed(content?.streams.orEmpty()) { _, stream ->
                        OutlinedButton(onClick = {
                            try {
                                uriHandler.openUri(session.externalStreamUrl(stream))
                            } catch (failure: Exception) {
                                playbackError = (failure.message ?: failure.toString()).take(2000)
                            }
                        }, modifier = Modifier.padding(horizontal = 20.dp)) { Text(stream.title) }
                    }
                }
                playbackError?.let { error -> item { Text(error, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error) } }
                itemsIndexed(content?.images.orEmpty(), key = { _, page -> page.index }) { _, page ->
                    Column {
                        Text(stringResource(Res.string.source_image_position, page.index + 1), Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall)
                        MediaImage(page.imageUrl, page.headers, images, chapter.title, Modifier.fillMaxWidth(), naturalSize = true)
                    }
                }
                if (content != null && content.text.isNullOrEmpty() && content.images.isEmpty() && content.streams.isEmpty()) {
                    item { Text(stringResource(Res.string.source_empty_content), Modifier.padding(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MediaError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.source_error_title), style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.source_retry)) }
        }
    }
}

@Composable
fun MediaImage(url: String?, headers: Map<String, String>, loader: SourceImageLoader, description: String,
    modifier: Modifier = Modifier, naturalSize: Boolean = false) {
    var bitmap by remember(url, headers, loader) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(url, headers, loader) { mutableStateOf<String?>(null) }
    var attempt by remember(url, headers, loader) { mutableStateOf(0) }
    LaunchedEffect(url, headers, loader, attempt) {
        if (url != null) {
            try {
                error = null
                bitmap = loader.load(url, headers)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = (failure.message ?: failure.toString()).take(500)
            }
        }
    }
    val image = bitmap
    val ratio = image?.let { (it.width.toFloat() / it.height).coerceIn(1f / 32f, 32f) } ?: 1f
    val sized = if (naturalSize) modifier.aspectRatio(ratio) else modifier
    Box(sized.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (image != null) Image(image, description, Modifier.fillMaxSize(), contentScale = if (naturalSize) ContentScale.Fit else ContentScale.Crop)
        else Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(when {
                url == null -> Res.string.source_image_missing
                error != null -> Res.string.source_image_failed
                else -> Res.string.source_image_loading
            }), style = MaterialTheme.typography.labelSmall)
            if (error != null) TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.source_retry)) }
        }
    }
}
