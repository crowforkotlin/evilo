package crow.evilo.network.ktor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SourceNetworkPolicyTest {
    private val policy = SourceNetworkPolicy(
        "test-source", setOf("https://api.example.com"), setOf("images.example.com"), "Test UA",
        apiPathPrefixes = setOf("/content/"),
    )

    @Test
    fun apiApprovalChecksOriginPathAndCredentials() {
        val approved = "https://api.example.com/content/items/example"
        assertEquals(approved, policy.approveApiUrl(approved))
        for (url in listOf(
            "https://api.example.com.evil.invalid/content/items/example",
            "https://api.example.com@evil.invalid/content/items/example",
            "https://user:password@api.example.com/content/items/example",
            "http://api.example.com/content/items/example",
            "https://api.example.com:8443/content/items/example",
            "https://api.example.com/account",
            "https://api.example.com/content/../account",
            "https://api.example.com/content/%2e%2e/account",
            "https://api.example.com/content/items/example#fragment",
        )) assertFailsWith<IllegalArgumentException> { policy.approveApiUrl(url) }
    }

    @Test
    fun imageApprovalIsExactAndDoesNotInheritApiCredentials() {
        assertEquals("https://images.example.com/one.jpg", policy.approveImageUrl("https://images.example.com/one.jpg"))
        for (url in listOf(
            "https://nested.images.example.com/one.jpg", "https://images.example.com.evil.invalid/one.jpg",
            "http://images.example.com/one.jpg", "https://images.example.com:8443/one.jpg",
            "https://user@images.example.com/one.jpg",
        )) assertFailsWith<IllegalArgumentException> { policy.approveImageUrl(url) }
        val headers = policy.imageHeaders(emptyMap())
        assertEquals("Test UA", headers["user-agent"])
        assertFalse(headers.containsKey("authorization"))
        for (name in listOf("Authorization", "Cookie", "X-Auth-Signature", "Host")) {
            assertFailsWith<IllegalArgumentException> { policy.imageHeaders(mapOf(name to "secret")) }
        }
    }

    @Test
    fun invalidImageApprovalsAndHeadersAreRejected() {
        for (host in listOf("*.example.com", "https://images.example.com", "127.0.0.1", "images.example.com/path", "images.example.com:443")) {
            assertFailsWith<IllegalArgumentException> { SourceNetworkPolicy.checkedImageHost(host) }
        }
        assertEquals("images.example.com", SourceNetworkPolicy.checkedImageHost(" Images.Example.Com "))
        for (headers in listOf(
            mapOf("User-Agent" to "UA", "user-agent" to "other"),
            mapOf("User-Agent" to "UA\r\nCookie: secret"),
            mapOf("authorization" to "secret"),
        )) assertFailsWith<IllegalArgumentException> { policy.apiHeaders(headers) }
    }

    @Test
    fun remotePackagesStayWithinOneOriginAndContentAddressedDirectory() {
        val policy = PackageNetworkPolicy("https://plugins.example.com/example/")
        assertEquals("https://plugins.example.com/example/manifest.wlm", policy.manifestUrl)
        val artifact = "https://plugins.example.com/example/artifacts/sha256/aa/${"a".repeat(64)}.pwasm"
        assertEquals(artifact, policy.approveUrl(artifact))
        for (url in listOf(
            artifact.replace("plugins.example.com", "evil.invalid"),
            artifact.replace("/example/", "/another/"),
            "https://plugins.example.com/example/artifacts/sha256/../../other.wasm",
            "https://plugins.example.com/example/artifacts/sha256/secret.txt",
            artifact + "?token=secret",
        )) assertFailsWith<IllegalArgumentException> { policy.approveUrl(url) }
    }

    @Test
    fun plainHttpPackagesRequireAnExplicitLoopbackLocation() {
        assertEquals("http://localhost:8080/plugin/manifest.wlm", PackageNetworkPolicy("http://localhost:8080/plugin").manifestUrl)
        assertFailsWith<IllegalArgumentException> { PackageNetworkPolicy("http://plugins.example.com/plugin") }
        assertFailsWith<IllegalArgumentException> { PackageNetworkPolicy("https://plugins.example.com/plugin?token=secret") }
    }
}
