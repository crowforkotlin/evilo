@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package crow.evilo.app

import coil3.Canvas
import coil3.Image
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.serviceLoaderEnabled
import coil3.decode.DataSource
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.Size
import crow.evilo.feature.reader.MediaSourceSession
import crow.evilo.feature.reader.PluginImageFormatException
import crow.evilo.feature.reader.PluginImageLimitException
import crow.evilo.feature.reader.PluginSessionClosedException
import crow.evilo.model.MediaKind
import crow.evilo.model.MediaStream
import crow.evilo.network.ktor.BoundedHttpTransport
import crow.evilo.network.ktor.HttpStatusException
import crow.evilo.network.ktor.NetworkPolicyException
import crow.evilo.network.ktor.NetworkPolicyViolation
import crow.evilo.plugin.api.MediaSource
import crow.evilo.plugin.api.PluginDescriptor
import crow.evilo.plugin.api.PluginOperation
import crow.evilo.plugin.api.PluginResult
import crow.evilo.plugin.api.SourceRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EviloImagesTest {
    @Test
    fun blankUrlsAreMissingRequestsAndRetriesUseWriteOnlyMemoryPolicy() = runTest {
        val fixture = ImageFixture()
        val session = ImageSession()
        try {
            val scope = fixture.images.sessionImages(session)
            for (url in listOf(null, "", "   ")) assertNull(scope.request(url, emptyMap()))
            assertNull(fixture.images.iconRequest("   ", false))
            assertEquals(0, session.approvals)
            val first = assertNotNull(scope.request(URL, emptyMap()))
            val retry = assertNotNull(scope.request(URL, emptyMap(), attempt = 1))
            assertEquals(CachePolicy.ENABLED, first.memoryCachePolicy)
            assertEquals(CachePolicy.WRITE_ONLY, retry.memoryCachePolicy)
            assertEquals(CachePolicy.DISABLED, retry.diskCachePolicy)
            val firstResult = fixture.intercept(first) { fixture.cachedResult(it) }
            val retryResult = fixture.intercept(retry) { fixture.cachedResult(it) }
            assertEquals(firstResult.request.memoryCacheKey, retryResult.request.memoryCacheKey)
        } finally { fixture.close() }
    }

    @Test
    fun cacheKeysIncludeTheSessionAndApprovedHeadersAndClosingOneScopeEvictsOnlyItsEntries() = runTest {
        val fixture = ImageFixture()
        val firstSession = ImageSession()
        val secondSession = ImageSession()
        try {
            val first = fixture.images.sessionImages(firstSession)
            val second = fixture.images.sessionImages(secondSession)
            val mutableHeaders = mutableMapOf("referer" to "https://example.com/one", "authorization" to "private-value")
            val request = assertNotNull(first.request(URL, mutableHeaders))
            mutableHeaders["referer"] = "https://example.com/changed"
            val one = fixture.intercept(request) { fixture.cachedResult(it) }
            assertEquals("https://example.com/one", firstSession.approvedRequests.last().headers["referer"])
            val anotherHeader = fixture.intercept(assertNotNull(first.request(URL, mapOf("referer" to "https://example.com/two")))) {
                fixture.cachedResult(it)
            }
            val otherSession = fixture.intercept(assertNotNull(second.request(URL, emptyMap()))) { fixture.cachedResult(it) }
            assertNotEquals(one.request.memoryCacheKey, anotherHeader.request.memoryCacheKey)
            assertNotEquals(one.request.memoryCacheKey, otherSession.request.memoryCacheKey)
            val cache = assertNotNull(fixture.images.loader.memoryCache)
            assertEquals(3, cache.keys.size)
            assertTrue(cache.keys.none { "private-value" in it.key || "signature" in it.key || "example.com" in it.key })
            first.close()
            first.close()
            assertFalse(first.connected.value)
            assertTrue(second.connected.value)
            assertEquals(0, firstSession.closeCalls)
            assertEquals(setOf(assertNotNull((otherSession as SuccessResult).memoryCacheKey)), cache.keys)
            fixture.images.close()
            assertFalse(second.connected.value)
            assertFailsWith<PluginSessionClosedException> { fixture.images.sessionImages(ImageSession()) }
        } finally { fixture.close() }
    }

    @Test
    fun approvalRunsBeforeAndAfterACacheResultAndRejectedRequestsNeverReachTheCache() = runTest {
        val fixture = ImageFixture()
        val session = ImageSession()
        var cacheLookups = 0
        try {
            val scope = fixture.images.sessionImages(session)
            val request = assertNotNull(scope.request(URL, emptyMap()))
            fixture.intercept(request) {
                cacheLookups++
                assertEquals(1, session.approvals)
                fixture.cachedResult(it)
            }
            assertEquals(2, session.approvals)
            session.allowed = false
            assertFailsWith<NetworkPolicyException> {
                fixture.intercept(request) { cacheLookups++; fixture.cachedResult(it) }
            }
            assertEquals(1, cacheLookups)
            assertEquals(3, session.approvals)
        } finally { fixture.close() }
    }

    @Test
    fun revocationAfterACacheResultPreventsItsPublicationAndRemovesTheCacheEntry() = runTest {
        val fixture = ImageFixture()
        val session = ImageSession()
        try {
            val request = assertNotNull(fixture.images.sessionImages(session).request(URL, emptyMap()))
            assertFailsWith<NetworkPolicyException> {
                fixture.intercept(request) {
                    val result = fixture.cachedResult(it)
                    session.allowed = false
                    result
                }
            }
            assertTrue(assertNotNull(fixture.images.loader.memoryCache).keys.isEmpty())
        } finally { fixture.close() }
    }

    @Test
    fun closingAScopeCancelsItsPendingLoadAndCannotLeaveALateCachedImage() = runTest {
        val fixture = ImageFixture()
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var published = false
        try {
            val scope = fixture.images.sessionImages(ImageSession())
            val request = assertNotNull(scope.request(URL, emptyMap()))
            val load = launch {
                fixture.intercept(request) {
                    started.complete(Unit)
                    withContext(NonCancellable) { gate.await() }
                    fixture.cachedResult(it)
                }
                published = true
            }
            started.await()
            scope.close()
            gate.complete(Unit)
            load.join()
            assertTrue(load.isCancelled)
            assertFalse(published)
            assertFalse(scope.connected.value)
            assertTrue(assertNotNull(fixture.images.loader.memoryCache).keys.isEmpty())
        } finally { gate.complete(Unit); fixture.close() }
    }

    @Test
    fun aClosedUnderlyingSessionMarksTheImageScopeDisconnected() = runTest {
        val fixture = ImageFixture()
        val session = ImageSession()
        try {
            val scope = fixture.images.sessionImages(session)
            val request = assertNotNull(scope.request(URL, emptyMap()))
            session.close()
            assertFailsWith<PluginSessionClosedException> { (request.data as EviloImageData).approve() }
            assertFalse(scope.connected.value)
        } finally { fixture.close() }
    }

    @Test
    fun decodedDimensionLimitsAlsoApplyToCachedResults() = runTest {
        val fixture = ImageFixture()
        try {
            val request = assertNotNull(fixture.images.sessionImages(ImageSession()).request(URL, emptyMap()))
            val failure = assertFailsWith<PluginImageLimitException> {
                fixture.intercept(request) { fixture.cachedResult(it, TestImage(16_385, 1)) }
            }
            assertEquals(16_384, failure.maxDimension)
            assertTrue(assertNotNull(fixture.images.loader.memoryCache).keys.isEmpty())
        } finally { fixture.close() }
    }

    @Test
    fun theCatalogFetcherKeepsRealHttpFailuresAndDoesNotDecodeErrorBodies() = runTest {
        val fixture = ImageFixture(HttpStatusCode.Forbidden)
        try {
            val request = assertNotNull(fixture.images.iconRequest("https://plugins.example.com/icon.png", false))
            val (fetcher, _) = assertNotNull(fixture.images.loader.components.newFetcher(request.data, Options(PlatformContext.INSTANCE), fixture.images.loader))
            assertEquals(403, assertFailsWith<HttpStatusException> { fetcher.fetch() }.statusCode)
        } finally { fixture.close() }
    }

    @Test
    fun theMediaFetcherRejectsCompressedByteLimitsAndMalformedContentBeforeDecoding() = runTest {
        val fixture = ImageFixture()
        val session = ImageSession()
        try {
            val request = assertNotNull(fixture.images.sessionImages(session).request(URL, emptyMap()))
            fun fetcher() = assertNotNull(fixture.images.loader.components.newFetcher(request.data, Options(PlatformContext.INSTANCE), fixture.images.loader)).first
            session.bytes = ByteArray(16 * 1024 * 1024 + 1)
            assertEquals(16L * 1024 * 1024, assertFailsWith<PluginImageLimitException> { fetcher().fetch() }.maxBytes)
            session.bytes = "<html>Denied</html>".encodeToByteArray()
            assertFailsWith<PluginImageFormatException> { fetcher().fetch() }
        } finally { fixture.close() }
    }

    @Test
    fun theRegisteredDecoderDelegatesWithoutConsumingTheSourceAndAllowsNullFallbacks() = runTest {
        val wrapper = PluginImageDecoderFactory()
        val image = TestImage()
        val calls = mutableListOf<Int>()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).serviceLoaderEnabled(false).components {
            add(wrapper)
            add(Decoder.Factory { _, _, _ -> Decoder { calls += 1; null } })
            add(Decoder.Factory { _, _, _ -> Decoder { calls += 2; DecodeResult(image, false) } })
        }.build()
        val options = Options(PlatformContext.INSTANCE)
        val result = SourceFetchResult(ImageSource(Buffer().write(PNG_HEADER), options.fileSystem), null, DataSource.NETWORK)
        try {
            val decoder = wrapper.create(result, options, loader)
            assertEquals(PNG_HEADER.size.toLong(), result.source.source().buffer.size)
            assertSame(image, decoder.decode()?.image)
            assertEquals(listOf(1, 2), calls)
        } finally { result.source.close(); loader.shutdown() }
    }

    private class ImageFixture(status: HttpStatusCode = HttpStatusCode.OK) {
        val transport = BoundedHttpTransport(HttpClient(MockEngine { respond("Image test response", status) }) { followRedirects = false })
        val images = EviloImages(PlatformContext.INSTANCE, transport)
        suspend fun intercept(request: ImageRequest, proceed: suspend (ImageRequest) -> ImageResult): ImageResult =
            images.loader.components.interceptors.first().intercept(TestChain(request, proceed))
        fun cachedResult(request: ImageRequest, image: Image = TestImage()): SuccessResult {
            val key = MemoryCache.Key(assertNotNull(request.memoryCacheKey))
            assertNotNull(images.loader.memoryCache)[key] = MemoryCache.Value(image)
            return SuccessResult(image, request, DataSource.MEMORY_CACHE, memoryCacheKey = key)
        }
        fun close() { try { images.close() } finally { transport.close() } }
    }

    private class TestChain(override val request: ImageRequest, private val next: suspend (ImageRequest) -> ImageResult,
        override val size: Size = Size.ORIGINAL) : Interceptor.Chain {
        override fun withRequest(request: ImageRequest): Interceptor.Chain = TestChain(request, next, size)
        override fun withSize(size: Size): Interceptor.Chain = TestChain(request, next, size)
        override suspend fun proceed(): ImageResult = next(request)
    }

    private class TestImage(override val width: Int = 2, override val height: Int = 3) : Image {
        override val size = width.toLong() * height * 4
        override val shareable = true
        override fun draw(canvas: Canvas) = Unit
    }

    private class ImageSession : MediaSourceSession {
        var allowed = true
        var closed = false
        var closeCalls = 0
        var approvals = 0
        var bytes = PNG_HEADER
        val approvedRequests = mutableListOf<SourceRequest>()
        override val source = object : MediaSource {
            override val descriptor = PluginDescriptor("image.test", "Image test", "1.0.0", 1, setOf(MediaKind.MANGA), listOf("en"))
            override suspend fun execute(operation: PluginOperation): PluginResult = error("This fixture does not execute works requests.")
            override suspend fun close() = Unit
        }
        override fun approveImageRequest(url: String, headers: Map<String, String>): SourceRequest {
            approvals++
            if (closed) throw PluginSessionClosedException()
            if (!allowed) throw NetworkPolicyException(NetworkPolicyViolation.ORIGIN_OR_PATH_NOT_APPROVED, "Origin rejected.")
            return SourceRequest(url = url, headers = headers).also { approvedRequests += it }
        }
        override suspend fun imageBytes(url: String, headers: Map<String, String>): ByteArray = bytes
        override fun externalStreamUrl(stream: MediaStream) = stream.url
        override suspend fun close() { closed = true; closeCalls++ }
    }

    private companion object {
        const val URL = "https://images.example.com/cover.png?signature=private-query"
        val PNG_HEADER = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, 0, 0, 0, 2, 0, 0, 0, 3)
    }
}
