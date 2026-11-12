package crow.evilo.network.ktor

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import crow.evilo.plugin.api.SourceRequest
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okio.Buffer

expect fun createEviloHttpClient(): HttpClient

expect val supportsLocalPluginManifests: Boolean

internal fun HttpClientConfig<*>.configureEviloHttp() {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 20_000
    }
}

class BoundedHttpTransport(
    private val client: HttpClient,
    private val requestTimeoutMillis: Long = 30_000,
    maxConcurrentRequests: Int = 4,
) {
    private val requests = Semaphore(maxConcurrentRequests)

    init {
        require(requestTimeoutMillis > 0) { "The HTTP request timeout must be positive." }
    }

    suspend fun fetch(url: String, headers: Map<String, String>, maxBytes: Int): HttpByteResponse {
        return fetch(SourceRequest(url = url, headers = headers), maxBytes)
    }

    suspend fun fetch(request: SourceRequest, maxBytes: Int): HttpByteResponse {
        val buffer = Buffer()
        val status = stream(request, maxBytes.toLong()) { bytes, offset, size -> buffer.write(bytes, offset, size) }
        return HttpByteResponse(status.statusCode, status.headers, buffer.readByteArray())
    }

    suspend fun stream(
        url: String,
        headers: Map<String, String>,
        maxBytes: Long,
        sink: (ByteArray, Int, Int) -> Unit,
    ): HttpStreamResponse {
        return stream(SourceRequest(url = url, headers = headers), maxBytes, sink)
    }

    private suspend fun stream(
        request: SourceRequest,
        maxBytes: Long,
        sink: (ByteArray, Int, Int) -> Unit,
    ): HttpStreamResponse {
        require(maxBytes > 0) { "The HTTP response byte limit must be positive." }
        val requestHeaders = request.headers.toMap()
        var sinkFailure: Throwable? = null
        try {
            return withTimeoutOrNull(requestTimeoutMillis) {
                requests.withPermit {
                    client.prepareRequest(request.url) {
                        method = HttpMethod.parse(request.method)
                        requestHeaders.forEach { (name, value) -> this.headers.append(name, value) }
                        request.body?.let { setBody(it) }
                    }.execute { response ->
                        if (response.status.value in 300..399) throw HttpRedirectRejectedException(response.status.value)
                        if (response.status.value == 0) throw HttpOpaqueResponseException()
                        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                        if (contentLength != null && contentLength !in 0..maxBytes) throw HttpResponseLimitException(maxBytes, response.status.value)
                        val channel = response.bodyAsChannel()
                        val chunk = ByteArray(8192)
                        var received = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val size = channel.readAvailable(chunk)
                            if (size == -1) break
                            received += size
                            if (received > maxBytes) throw HttpResponseLimitException(maxBytes, response.status.value)
                            try { sink(chunk, 0, size) }
                            catch (failure: Throwable) { sinkFailure = failure; throw failure }
                        }
                        HttpStreamResponse(
                            response.status.value,
                            response.headers.entries().associate { (name, values) -> name to values.joinToString(", ") },
                        )
                    }
                }
            } ?: throw HttpTransportTimeoutException(requestTimeoutMillis)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: HttpTransportException) {
            throw failure
        } catch (failure: Exception) {
            if (failure === sinkFailure) throw failure
            throw HttpNetworkException(failure)
        }
    }

    fun close() = client.close()
}

data class HttpStreamResponse(val statusCode: Int, val headers: Map<String, String>)

data class HttpByteResponse(val statusCode: Int, val headers: Map<String, String>, val bytes: ByteArray)
