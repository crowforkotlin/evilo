package crow.evilo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import crow.evilo.app.resources.*
import crow.evilo.feature.reader.ImageFailure
import crow.evilo.feature.reader.ImageFailureKind
import crow.evilo.feature.reader.imageFailure
import org.jetbrains.compose.resources.stringResource

internal enum class ImagePresentation { ICON, COVER, PAGE }

@Composable
internal fun EviloAsyncImage(
    request: ImageRequest?,
    loader: ImageLoader,
    description: String,
    modifier: Modifier = Modifier,
    naturalSize: Boolean = false,
    contentScale: ContentScale = if (naturalSize) ContentScale.Fit else ContentScale.Crop,
    onRetry: (() -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    presentation: ImagePresentation = if (naturalSize) ImagePresentation.PAGE else ImagePresentation.COVER,
    address: String? = null,
    pageNumber: Int? = null,
    onPermissions: (() -> Unit)? = null,
    onFailure: ((ImageFailure?) -> Unit)? = null,
) {
    var state by remember(request, loader) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    var showDetails by remember(request, loader) { mutableStateOf(false) }
    val error = (state as? AsyncImagePainter.State.Error)?.result?.throwable
    val failure = remember(error, address) { error?.let { imageFailure(it, address) } }
    val failureCallback by rememberUpdatedState(onFailure)
    LaunchedEffect(request, failure) { failureCallback?.invoke(failure) }
    val image = (state as? AsyncImagePainter.State.Success)?.result?.image
    val ratio = image?.let { (it.width.toFloat() / it.height).coerceIn(1f / 32f, 32f) } ?: 1f
    val sized = if (naturalSize) modifier.aspectRatio(ratio) else modifier
    val status = if (request == null) stringResource(when (presentation) {
        ImagePresentation.PAGE -> Res.string.image_missing_page
        ImagePresentation.ICON -> Res.string.image_missing_icon
        ImagePresentation.COVER -> Res.string.source_image_missing
    })
        else failure?.let { imageFailureLabel(it) } ?: stringResource(Res.string.image_loading)
    BoxWithConstraints(sized.background(MaterialTheme.colorScheme.surfaceContainerHighest).semantics {
        if (image == null) stateDescription = status
    }, contentAlignment = Alignment.Center) {
        if (request != null) AsyncImage(model = request, contentDescription = description, imageLoader = loader,
            modifier = Modifier.fillMaxSize(), contentScale = contentScale, onState = { state = it })
        if (image == null) {
            if (placeholder != null) placeholder()
            else Column(Modifier.padding(EviloSpacing.Medium), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                if (failure == null) {
                    if (request != null && presentation == ImagePresentation.PAGE) CircularProgressIndicator(Modifier.size(EviloLayout.IconSize))
                    else Icon(EviloIcons.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (request == null || presentation == ImagePresentation.PAGE) Text(status, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    if (presentation == ImagePresentation.PAGE) Text(pageNumber?.let { stringResource(Res.string.image_page_failed_number, it) }
                        ?: stringResource(Res.string.image_page_failed), style = MaterialTheme.typography.titleSmall)
                    val label = if (presentation == ImagePresentation.COVER && failure.kind in setOf(ImageFailureKind.NETWORK, ImageFailureKind.HTTP, ImageFailureKind.UNKNOWN))
                        stringResource(Res.string.image_cover_failed) else imageFailureLabel(failure)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                        if (failure.canRetry && onRetry != null && this@BoxWithConstraints.maxWidth >= EviloLayout.MinimumTouchTarget * 2 + EviloSpacing.Small + EviloSpacing.Medium * 2) IconButton(onClick = onRetry) {
                            Icon(EviloIcons.Refresh, stringResource(Res.string.image_retry_for, description))
                        }
                        IconButton(onClick = { showDetails = true }) { Icon(EviloIcons.Info, stringResource(Res.string.image_details)) }
                    }
                    if (presentation == ImagePresentation.PAGE && failure.kind == ImageFailureKind.PERMISSION && onPermissions != null)
                        TextButton(onClick = onPermissions) { Text(stringResource(Res.string.plugin_permissions)) }
                }
            }
        }
    }
    if (showDetails && failure != null) EviloImageErrorDetails(failure, onDismiss = { showDetails = false }, onPermissions = onPermissions, onRetry = onRetry)
}
