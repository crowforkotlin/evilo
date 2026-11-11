package crow.evilo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import crow.evilo.app.resources.Res
import crow.evilo.app.resources.source_image_failed
import crow.evilo.app.resources.source_image_loading
import crow.evilo.app.resources.source_image_missing
import crow.evilo.app.resources.source_retry
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EviloAsyncImage(
    request: ImageRequest?,
    loader: ImageLoader,
    description: String,
    modifier: Modifier = Modifier,
    naturalSize: Boolean = false,
    contentScale: ContentScale = if (naturalSize) ContentScale.Fit else ContentScale.Crop,
    onRetry: () -> Unit = {},
    placeholder: (@Composable () -> Unit)? = null,
) {
    var state by remember(request, loader) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val image = (state as? AsyncImagePainter.State.Success)?.result?.image
    val ratio = image?.let { (it.width.toFloat() / it.height).coerceIn(1f / 32f, 32f) } ?: 1f
    val sized = if (naturalSize) modifier.aspectRatio(ratio) else modifier
    Box(sized.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (request != null) AsyncImage(model = request, contentDescription = description, imageLoader = loader,
            modifier = Modifier.fillMaxSize(), contentScale = contentScale, onState = { state = it })
        if (image == null) {
            if (placeholder != null) placeholder()
            else Column(Modifier.padding(EviloSpacing.Medium), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                val error = (state as? AsyncImagePainter.State.Error)?.result?.throwable
                if (request != null && error == null) CircularProgressIndicator(Modifier.size(EviloLayout.IconSize))
                Text(stringResource(when {
                    request == null -> Res.string.source_image_missing
                    error != null -> Res.string.source_image_failed
                    else -> Res.string.source_image_loading
                }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error != null) {
                    Text((error.message ?: error.toString()).take(500), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.source_retry)) }
                }
            }
        }
    }
}
