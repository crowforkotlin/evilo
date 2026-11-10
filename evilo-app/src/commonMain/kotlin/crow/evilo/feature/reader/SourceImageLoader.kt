package crow.evilo.feature.reader

import androidx.compose.ui.graphics.ImageBitmap
import crow.evilo.app.decodePluginImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

fun interface SourceImageLoader {
    suspend fun load(url: String, headers: Map<String, String>): ImageBitmap
}

internal fun MediaSourceSession.imageLoader(): SourceImageLoader = SourceImageLoader { url, headers ->
    withContext(Dispatchers.Default) {
        val bytes = imageBytes(url, headers)
        currentCoroutineContext().ensureActive()
        decodePluginImage(bytes)
    }
}
