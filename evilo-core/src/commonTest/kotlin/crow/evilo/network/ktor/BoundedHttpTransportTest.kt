package crow.evilo.network.ktor

import crow.evilo.plugin.api.SOURCE_API_VERSION
import crow.evilo.plugin.api.SourceMetadata
import crow.evilo.plugin.api.SourceRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class BoundedHttpTransportTest {
    @Test
    fun responseBodiesAreBoundedEvenWithoutAContentLength() = runTest {
        val client = HttpClient(MockEngine { respond("12345") }) { followRedirects = false }
        val transport = BoundedHttpTransport(client)
        try {
            val response = transport.fetch("https://example.com/", emptyMap(), 5)
            assertContentEquals("12345".encodeToByteArray(), response.bytes)
            assertFailsWith<IllegalStateException> { transport.fetch("https://example.com/", emptyMap(), 4) }
        } finally {
            transport.close()
        }
    }

    @Test
    fun redirectsAreRejectedBeforeTheirBodyIsDelivered() = runTest {
        val client = HttpClient(MockEngine { respond("redirect", HttpStatusCode.Found, headersOf("Location", "https://other.invalid/")) }) {
            followRedirects = false
        }
        val transport = BoundedHttpTransport(client)
        var delivered = false
        try {
            assertFailsWith<IllegalStateException> {
                transport.stream("https://example.com/", emptyMap(), 1024) { _, _, _ -> delivered = true }
            }
            assertFalse(delivered)
        } finally {
            transport.close()
        }
    }

    @Test
    fun claimedOversizeResponsesFailBeforeReadingBytes() = runTest {
        val client = HttpClient(MockEngine { respond("body", headers = headersOf("Content-Length", "1000")) }) {
            followRedirects = false
        }
        val transport = BoundedHttpTransport(client)
        try {
            assertFailsWith<IllegalStateException> { transport.fetch("https://example.com/", emptyMap(), 10) }
        } finally {
            transport.close()
        }
    }

    @Test
    fun sourceRequestsDoNotAcquireUnapprovedHeadersOrHosts() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals("application/json", request.headers["accept"])
            assertFalse(request.headers.contains("Authorization"))
            respond("{}")
        }) { followRedirects = false }
        val transport = BoundedHttpTransport(client)
        val policy = SourceNetworkPolicy("source", setOf("https://api.example.com"), emptySet(), "UA")
        val http = PolicySourceHttpClient(transport, policy)
        val metadata = SourceMetadata("source", "Source", "en", "1", SOURCE_API_VERSION)
        try {
            http.execute(metadata, SourceRequest("https://api.example.com/content/items", headers = mapOf("accept" to "application/json")))
            assertFailsWith<IllegalArgumentException> {
                http.execute(metadata, SourceRequest("https://other.invalid/content/items"))
            }
            assertEquals(1, requests)
        } finally {
            transport.close()
        }
    }

    @Test
    fun callerCancellationIsNotConvertedToAnHttpError() = runTest {
        val client = HttpClient(MockEngine { throw CancellationException("Cancelled HTTP.") }) { followRedirects = false }
        val transport = BoundedHttpTransport(client)
        try {
            assertFailsWith<CancellationException> { transport.fetch("https://example.com/", emptyMap(), 1024) }
        } finally {
            transport.close()
        }
    }

    @Test
    fun redirectsAndByteLimitsKeepTypedStatusInformation() = runTest {
        var redirect = true
        val client = HttpClient(MockEngine {
            if (redirect) respond("redirect", HttpStatusCode.Found)
            else respond("oversized", HttpStatusCode.Forbidden)
        }) { followRedirects = false }
        val transport = BoundedHttpTransport(client)
        try {
            assertEquals(302, assertFailsWith<HttpRedirectRejectedException> { transport.fetch("https://example.com/", emptyMap(), 4) }.statusCode)
            redirect = false
            val limit = assertFailsWith<HttpResponseLimitException> { transport.fetch("https://example.com/", emptyMap(), 4) }
            assertEquals(403, limit.statusCode)
            assertEquals(4L, limit.maxBytes)
        } finally { transport.close() }
    }

    @Test
    fun sinkFailuresAreNotRenamedAsNetworkFailures() = runTest {
        val client = HttpClient(MockEngine { respond("data") }) { followRedirects = false }
        val transport = BoundedHttpTransport(client)
        val failure = IllegalStateException("Artifact storage failed.")
        try {
            assertSame(failure, assertFailsWith<IllegalStateException> {
                transport.stream("https://example.com/", emptyMap(), 1024) { _, _, _ -> throw failure }
            })
        } finally { transport.close() }
    }
}
