package crow.evilo.app

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.serviceLoaderEnabled
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.maxBitmapSize
import coil3.size.Size
import crow.evilo.feature.reader.MediaSourceSession
import crow.evilo.network.ktor.BoundedHttpTransport
import crow.evilo.network.ktor.PluginUrlPolicy
import crow.evilo.plugin.api.SourceRequest
import crow.evilo.storage.StoreLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer

internal class EviloImages(
    private val context: PlatformContext,
    private val transport: BoundedHttpTransport,
) {
    private val lock = StoreLock()
    private val sessions = mutableSetOf<SessionImages>()
    private var nextSession = 0L
    private var closed = false
    private val cache = MemoryCache.Builder().maxSizeBytes(32L * 1024 * 1024).weakReferencesEnabled(false).build()

    val loader: ImageLoader = ImageLoader.Builder(context)
        .memoryCache(cache)
        .diskCache(null)
        .serviceLoaderEnabled(false)
        .fetcherCoroutineContext(Dispatchers.Default)
        .decoderCoroutineContext(Dispatchers.Default)
        .components {
            // This interceptor runs before Coil's memory cache, including on cache hits.
            add(coil3.intercept.Interceptor { chain ->
                ensureOpen()
                val data = chain.request.data as? EviloImageData ?: error("An approved evilo image request is required.")
                val approved = data.approve()
                val request = chain.request.newBuilder()
                    .memoryCacheKey(data.cachePrefix + imageCacheDigest(approved))
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
                val result = chain.withRequest(request).proceed()
                try {
                    ensureOpen()
                    data.approve()
                    if (result is SuccessResult) {
                        requirePluginDimensions(result.image.width, result.image.height, data.limits.maxDimension, data.limits.maxPixels)
                    }
                } catch (failure: Throwable) {
                    if (result is SuccessResult) result.memoryCacheKey?.let(cache::remove)
                    throw failure
                }
                result
            })
            add(Fetcher.Factory<EviloImageData> { data, options, _ ->
                Fetcher {
                    ensureOpen()
                    val bytes = data.bytes(transport)
                    currentCoroutineContext().ensureActive()
                    ensureOpen()
                    data.approve()
                    require(bytes.size <= data.limits.maxBytes) { "Image exceeds its compressed byte limit." }
                    validatePluginImage(bytes, data.limits.maxDimension, data.limits.maxPixels)
                    SourceFetchResult(ImageSource(Buffer().write(bytes), options.fileSystem), mimeType = null, dataSource = DataSource.NETWORK)
                }
            })
        }
        .build()

    fun iconRequest(url: String, allowHttp: Boolean, attempt: Int = 0): ImageRequest =
        request(CatalogImageData(url, allowHttp, attempt))

    fun sessionImages(session: MediaSourceSession): SessionImages = lock.withLock {
        check(!closed && nextSession < Long.MAX_VALUE) { "The image loader is closed." }
        SessionImages(this, session, "media:${++nextSession}:").also { sessions += it }
    }

    internal fun request(data: EviloImageData, naturalSize: Boolean = false): ImageRequest = ImageRequest.Builder(context)
        .data(data)
        .diskCachePolicy(CachePolicy.DISABLED)
        .maxBitmapSize(Size(data.limits.maxDimension, data.limits.maxDimension))
        .apply { if (naturalSize) size(Size.ORIGINAL) }
        .build()

    internal fun ensureOpen() = lock.withLock { check(!closed) { "The image loader is closed." } }

    internal fun release(session: SessionImages, prefix: String) {
        lock.withLock { sessions -= session }
        cache.keys.filter { it.key.startsWith(prefix) }.forEach(cache::remove)
    }

    fun close() {
        val active = lock.withLock {
            if (closed) return@withLock null
            closed = true
            sessions.toList().also { sessions.clear() }
        } ?: return
        active.forEach(SessionImages::close)
        loader.shutdown()
    }
}

internal class SessionImages internal constructor(
    private val images: EviloImages,
    private val session: MediaSourceSession,
    internal val cachePrefix: String,
) {
    private val lock = StoreLock()
    private var closed = false
    val loader get() = images.loader

    fun request(url: String?, headers: Map<String, String>, attempt: Int = 0, naturalSize: Boolean = false): ImageRequest? =
        url?.let { images.request(MediaImageData(this, it, headers.toMap(), attempt), naturalSize) }

    internal fun approve(url: String, headers: Map<String, String>): SourceRequest {
        images.ensureOpen()
        lock.withLock { check(!closed) { "This plugin image session is closed." } }
        return session.approveImageRequest(url, headers)
    }

    internal suspend fun bytes(url: String, headers: Map<String, String>): ByteArray {
        val approved = approve(url, headers)
        return session.imageBytes(approved.url, approved.headers)
    }

    fun close() {
        val wasOpen = lock.withLock { (!closed).also { closed = true } }
        if (wasOpen) images.release(this, cachePrefix)
    }
}

internal data class PluginImageLimits(val maxBytes: Int, val maxDimension: Int, val maxPixels: Long)

internal sealed interface EviloImageData {
    val limits: PluginImageLimits
    val cachePrefix: String
    fun approve(): SourceRequest
    suspend fun bytes(transport: BoundedHttpTransport): ByteArray
}

private data class CatalogImageData(val url: String, val allowHttp: Boolean, val attempt: Int) : EviloImageData {
    override val limits get() = ICON_LIMITS
    override val cachePrefix get() = "catalog:$allowHttp:"
    override fun approve() = SourceRequest(url = PluginUrlPolicy.check(url, allowHttp).toString())
    override suspend fun bytes(transport: BoundedHttpTransport): ByteArray {
        val response = transport.fetch(approve(), limits.maxBytes)
        require(response.statusCode in 200..299) { "Plugin icon request failed: HTTP ${response.statusCode}." }
        return response.bytes
    }
}

private data class MediaImageData(val session: SessionImages, val url: String, val headers: Map<String, String>, val attempt: Int) : EviloImageData {
    override val limits get() = MEDIA_LIMITS
    override val cachePrefix get() = session.cachePrefix
    override fun approve() = session.approve(url, headers)
    override suspend fun bytes(transport: BoundedHttpTransport) = session.bytes(url, headers)
}

private val ICON_LIMITS = PluginImageLimits(1024 * 1024, 512, 512L * 512)
private val MEDIA_LIMITS = PluginImageLimits(16 * 1024 * 1024, 16_384, 32L * 1024 * 1024)

private fun imageCacheDigest(request: SourceRequest): String {
    val buffer = Buffer()
    fun append(value: String) {
        val bytes = value.encodeToByteArray()
        buffer.writeInt(bytes.size).write(bytes)
    }
    append(request.url)
    request.headers.entries.sortedBy { it.key }.forEach { (name, value) -> append(name); append(value) }
    return buffer.readByteString().sha256().hex()
}
