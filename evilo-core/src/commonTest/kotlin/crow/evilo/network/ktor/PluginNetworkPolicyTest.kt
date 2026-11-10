package crow.evilo.network.ktor

import crow.evilo.plugin.api.HttpAccess
import crow.evilo.plugin.api.PluginPermissions
import crow.evilo.plugin.api.SourceRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PluginNetworkPolicyTest {
    private val permissions = PluginPermissions(
        api = HttpAccess(setOf("https://api.example.com"), setOf("/content/"), setOf("GET", "POST"),
            setOf("accept", "content-type", "authorization")),
        media = HttpAccess(setOf("https://images.example.com"), headers = setOf("accept")),
    )
    private val policy = PluginNetworkPolicy("test.plugin", permissions, false)

    @Test
    fun approvedPostKeepsOnlyExplicitlyDeclaredRequestData() {
        val request = SourceRequest("https://api.example.com/content/search", "POST",
            mapOf("Content-Type" to "application/json"), "{}")
        assertEquals(request.copy(headers = mapOf("content-type" to "application/json")), policy.apiRequest(request))
    }

    @Test
    fun originPathCredentialsAndMethodsCannotEscapeTheirApprovals() {
        for (url in listOf(
            "https://api.example.com.evil.invalid/content/items",
            "https://api.example.com:8443/content/items",
            "https://user@api.example.com/content/items",
            "https://api.example.com/account",
            "https://api.example.com/content/%252e%252e/account",
        )) assertFailsWith<IllegalArgumentException> { policy.apiRequest(SourceRequest(url)) }
        assertFailsWith<IllegalArgumentException> { policy.apiRequest(SourceRequest("https://api.example.com/content/items", "DELETE")) }
    }

    @Test
    fun mediaDoesNotInheritApiAuthorizationAndHeadersCannotBeDuplicated() {
        assertFailsWith<IllegalArgumentException> {
            policy.mediaRequest("https://images.example.com/cover.png", mapOf("authorization" to "test"))
        }
        assertFailsWith<IllegalArgumentException> {
            policy.apiRequest(SourceRequest("https://api.example.com/content/items", headers = mapOf("Accept" to "json", "accept" to "other")))
        }
        assertFailsWith<IllegalArgumentException> {
            policy.apiRequest(SourceRequest("https://api.example.com/content/items", headers = mapOf("accept" to "json\r\nCookie: test")))
        }
    }

    @Test
    fun requestBodyLimitUsesBytesAndBodylessMethodsRejectPayloads() {
        assertFailsWith<IllegalArgumentException> {
            policy.apiRequest(SourceRequest("https://api.example.com/content/items", body = "{}"))
        }
        assertFailsWith<IllegalArgumentException> {
            policy.apiRequest(SourceRequest("https://api.example.com/content/items", "POST", body = "x".repeat(64 * 1024 + 1)))
        }
    }

    @Test
    fun cleartextAndWildcardOriginsCannotBeImplicitApprovals() {
        val cleartext = permissions.copy(api = permissions.api.copy(origins = setOf("http://api.example.com")))
        assertFailsWith<IllegalArgumentException> { PluginNetworkPolicy("test.plugin", cleartext, false) }
        val approved = PluginNetworkPolicy("test.plugin", cleartext, true)
        val request = SourceRequest("http://api.example.com/content/items")
        assertEquals(request, approved.apiRequest(request))
        assertFailsWith<IllegalArgumentException> {
            PluginNetworkPolicy("test.plugin", permissions.copy(api = permissions.api.copy(origins = setOf("https://*.example.com"))), false)
        }
    }
}
