package crow.evilo.app

import androidx.compose.ui.graphics.ImageBitmap
import crow.evilo.network.ktor.BoundedHttpTransport
import crow.evilo.network.ktor.PluginUrlPolicy
import crow.evilo.feature.browse.PluginIconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class CatalogIconLoader(private val transport: BoundedHttpTransport) : PluginIconLoader {
    private val cache = linkedMapOf<String, ImageBitmap>()
    private val lock = Mutex()

    override suspend fun load(url: String, allowHttp: Boolean): ImageBitmap = withContext(Dispatchers.Default) {
        val checked = PluginUrlPolicy.check(url, allowHttp).toString()
        lock.withLock { cache[checked]?.let { return@withContext it } }
        val response = transport.fetch(checked, emptyMap(), 1024 * 1024)
        require(response.statusCode in 200..299) { "Plugin icon request failed: HTTP ${response.statusCode}." }
        val image = decodePluginImage(response.bytes, maxDimension = 512, maxPixels = 512L * 512)
        lock.withLock {
            cache.remove(checked)
            cache[checked] = image
            while (cache.size > 32) cache.remove(cache.keys.first())
        }
        image
    }
}
