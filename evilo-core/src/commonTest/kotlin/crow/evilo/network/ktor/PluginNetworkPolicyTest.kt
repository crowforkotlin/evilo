package crow.evilo.network.ktor

import crow.evilo.plugin.api.SourceRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PluginNetworkPolicyTest {
    private val policy = PluginNetworkPolicy("test.plugin", allowHttp = false)

    @Test
    fun anyHostIsReachable() {
        for (url in listOf(
            "https://api.example.com/content/search",
            "https://anything.invalid/path",
            "https://a.b.example.org/x?y=1",
        )) assertEquals(url, policy.approve(SourceRequest(url)).url)
    }

    @Test
    fun anyMethodIsForwarded() {
        for (method in listOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE")) {
            val request = SourceRequest("https://api.example.com/content/items", method, body = "{}")
            assertEquals(method, policy.approve(request).method)
        }
    }

    @Test
    fun headersAreLowercasedAndKept() {
        val request = SourceRequest("https://api.example.com/content/items", headers = mapOf(
            "Content-Type" to "application/json", "AUTHORIZATION" to "abc",
        ))
        assertEquals(
            mapOf("content-type" to "application/json", "authorization" to "abc"),
            policy.approve(request).headers,
        )
    }

    @Test
    fun duplicateForbiddenAndMalformedHeadersAreRejected() {
        assertFailsWith<NetworkPolicyException> {
            policy.approve(SourceRequest("https://api.example.com/x", headers = mapOf("Accept" to "a", "accept" to "b")))
        }
        assertFailsWith<NetworkPolicyException> {
            policy.approve(SourceRequest("https://api.example.com/x", headers = mapOf("host" to "evil.invalid")))
        }
        assertFailsWith<NetworkPolicyException> {
            policy.approve(SourceRequest("https://api.example.com/x", headers = mapOf("accept" to "a\r\nCookie: b")))
        }
    }

    @Test
    fun requestBodyIsBoundedInBytes() {
        assertEquals("{}", policy.approve(SourceRequest("https://api.example.com/x", "POST", body = "{}")).body)
        assertFailsWith<NetworkPolicyException> {
            policy.approve(SourceRequest("https://api.example.com/x", "POST", body = "x".repeat(64 * 1024 + 1)))
        }
    }

    @Test
    fun cleartextNeedsTheSubscriptionConsent() {
        assertFailsWith<NetworkPolicyException> { policy.approve(SourceRequest("http://api.example.com/x")) }
        val permissive = PluginNetworkPolicy("test.plugin", allowHttp = true)
        assertEquals("http://api.example.com/x",
            permissive.approve(SourceRequest("http://api.example.com/x")).url.replace(":80", ""))
    }

    @Test
    fun credentialsTraversalAndNonHttpSchemesAreRejected() {
        for (url in listOf(
            "https://user@api.example.com/x",
            "https://api.example.com/%252e%252e/etc",
            "ftp://api.example.com/x",
            "not-a-url",
        )) assertFailsWith<NetworkPolicyException> { policy.approve(SourceRequest(url)) }
    }

    @Test
    fun imageRequestFailureReportsTheStatus() = runTest {
        var requests = 0
        val transport = BoundedHttpTransport(HttpClient(MockEngine {
            requests++
            respond("denied", HttpStatusCode.Forbidden)
        }) { followRedirects = false }, requestTimeoutMillis = 0)
        try {
            assertEquals(403, assertFailsWith<HttpStatusException> {
                PluginHttpClient(transport, policy).imageBytes("https://images.example.com/cover.png", emptyMap())
            }.statusCode)
            assertEquals(1, requests)
        } finally { transport.close() }
    }
}
