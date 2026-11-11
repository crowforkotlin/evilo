package crow.evilo.network.ktor

import crow.evilo.plugin.api.HttpAccess
import crow.evilo.plugin.api.PluginPermissions
import crow.evilo.plugin.api.SourceHttpClient
import crow.evilo.plugin.api.SourceMetadata
import crow.evilo.plugin.api.SourceRequest
import crow.evilo.plugin.api.SourceResponse
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.decodeURLPart

object PluginUrlPolicy {
    fun check(value: String, allowHttp: Boolean = false): Url {
        require(value.length <= 4096 && (value.startsWith("https://") || value.startsWith("http://"))) {
            "An absolute HTTP(S) URL is required."
        }
        require(value.none { it <= ' ' || it == '\u007f' || it == '\\' || it == '#' }) { "Invalid characters in URL." }
        val url = Url(value)
        val authority = value.substringAfter("://").substringBefore('/').substringBefore('?')
        require(url.host.isNotBlank() && url.host.length <= 253 && url.user == null && '@' !in authority) { "URL credentials or oversized hostnames are not allowed." }
        require(url.protocol == URLProtocol.HTTPS || (allowHttp && url.protocol == URLProtocol.HTTP)) {
            "HTTP requires explicit cleartext consent; use HTTPS where possible."
        }
        url.rawSegments.forEach { segment ->
            var decoded = segment
            repeat(3) {
                decoded = decoded.decodeURLPart()
                require(decoded.none { it <= ' ' || it == '\u007f' || it == '\\' } &&
                    decoded.split('/').none { it == "." || it == ".." }) { "URL path traversal is not allowed." }
            }
        }
        return url
    }

    fun origin(url: Url): String = "${url.protocol.name}://${url.host.lowercase()}:${url.port}"
}

fun validatePluginPermissions(permissions: PluginPermissions, allowHttp: Boolean) {
    listOf(permissions.api, permissions.media).forEach { access ->
        require(access.origins.size <= 32 && access.pathPrefixes.size in 1..32 && access.headers.size <= 32) {
            "Plugin network approvals exceed their limits."
        }
        access.origins.forEach { origin ->
            val url = PluginUrlPolicy.check(origin, allowHttp)
            require(url.encodedPath in listOf("", "/") && url.encodedQuery.isEmpty() && '*' !in url.host) {
                "Network origins must be exact origins without paths, queries, or wildcards."
            }
        }
        require(access.pathPrefixes.all { prefix ->
            prefix.startsWith('/') && prefix.none { it <= ' ' || it in "\\%?#" } &&
                prefix.split('/').none { it in listOf(".", "..") }
        }) { "Invalid approved path prefix." }
        require(access.methods.isNotEmpty() && access.methods.all { it in APPROVED_METHODS }) { "Unsupported HTTP methods." }
        require(access.headers.all { HEADER_NAME.matches(it) && it == it.lowercase() && it !in FORBIDDEN_HEADERS }) {
            "Invalid or forbidden HTTP header approval."
        }
    }
    require(permissions.media.methods == setOf("GET")) { "Media downloads only support GET." }
}

class PluginNetworkPolicy(
    val pluginId: String,
    val permissions: PluginPermissions,
    private val allowHttp: Boolean,
) {
    init {
        validatePluginPermissions(permissions, allowHttp)
    }

    fun apiRequest(request: SourceRequest): SourceRequest = approve(request, permissions.api)
    fun mediaRequest(url: String, headers: Map<String, String>): SourceRequest =
        approve(SourceRequest(url = url, headers = headers), permissions.media)

    private fun approve(request: SourceRequest, access: HttpAccess): SourceRequest {
        val url = PluginUrlPolicy.check(request.url, allowHttp)
        val approvedOrigins = access.origins.map { PluginUrlPolicy.origin(PluginUrlPolicy.check(it, allowHttp)) }.toSet()
        require(PluginUrlPolicy.origin(url) in approvedOrigins && access.pathPrefixes.any { prefix ->
            url.encodedPath == prefix || url.encodedPath.startsWith(if (prefix.endsWith('/')) prefix else "$prefix/")
        }) { "Network URL is not approved for plugin $pluginId: ${url.host}." }
        require(request.method in access.methods) { "HTTP method ${request.method} is not approved." }
        val body = request.body
        require(body == null || (request.method in setOf("POST", "PUT", "PATCH") &&
            body.encodeToByteArray().size <= 64 * 1024)) { "Invalid or oversized HTTP request body." }
        require(request.headers.size <= 32) { "Too many HTTP headers." }
        val headers = mutableMapOf<String, String>()
        request.headers.forEach { (name, value) ->
            val normalized = name.lowercase()
            require(normalized in access.headers && normalized !in headers && normalized !in FORBIDDEN_HEADERS) {
                "HTTP header '$name' is not approved."
            }
            require(value.length <= 4096 && value.none { it < ' ' || it == '\u007f' }) { "Invalid HTTP header value." }
            headers[normalized] = value
        }
        return request.copy(url = url.toString(), headers = headers)
    }
}

class PluginHttpClient(
    private val transport: BoundedHttpTransport,
    private val policy: PluginNetworkPolicy,
) : SourceHttpClient {
    override suspend fun execute(source: SourceMetadata, request: SourceRequest): SourceResponse {
        require(source.id == policy.pluginId) { "HTTP permission belongs to another plugin." }
        val approved = policy.apiRequest(request)
        val response = transport.fetch(approved, 2 * 1024 * 1024)
        return SourceResponse(approved.url, response.statusCode, response.headers,
            response.bytes.decodeToString(throwOnInvalidSequence = true))
    }

    suspend fun imageBytes(url: String, headers: Map<String, String>): ByteArray {
        val approved = policy.mediaRequest(url, headers)
        val response = transport.fetch(approved, 16 * 1024 * 1024)
        require(response.statusCode in 200..299) { "Image request failed: HTTP ${response.statusCode}." }
        return response.bytes
    }

    fun approvedStreamUrl(url: String, headers: Map<String, String>): String = policy.mediaRequest(url, headers).url
    fun approvedImageRequest(url: String, headers: Map<String, String>): SourceRequest = policy.mediaRequest(url, headers)
}

private val APPROVED_METHODS = setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE")
private val HEADER_NAME = Regex("[a-z0-9!#$%&'*+.^_`|~-]+")
private val FORBIDDEN_HEADERS = setOf(
    "cookie", "cookie2", "host", "connection", "content-length", "transfer-encoding", "upgrade", "proxy-authorization",
    "proxy-connection", "forwarded", "x-forwarded-for", "x-forwarded-host", "origin", "access-control-request-headers",
    "access-control-request-method", "sec-fetch-site", "sec-fetch-mode", "sec-fetch-dest",
)
