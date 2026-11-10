package crow.evilo.network.ktor

import io.ktor.http.URLProtocol
import io.ktor.http.Url

class SourceNetworkPolicy(
    val sourceId: String,
    apiOrigins: Set<String>,
    imageHosts: Set<String>,
    private val imageUserAgent: String,
    private val apiPathPrefixes: Set<String> = setOf("/"),
    private val apiHeaderNames: Set<String> = setOf("user-agent", "accept"),
) {
    private val approvedApiOrigins = apiOrigins.map { origin ->
        val url = checkedUrl(origin)
        require(url.protocol == URLProtocol.HTTPS && url.port == 443 && url.encodedPath in listOf("", "/")) {
            "API origins must be HTTPS origins on port 443, without paths."
        }
        require(url.encodedQuery.isEmpty()) { "API origins must not contain a query." }
        url.origin()
    }.toSet()
    private val approvedImageHosts = imageHosts.map(::checkedImageHost).toSet()

    init {
        require(sourceId.isNotBlank() && approvedApiOrigins.isNotEmpty()) { "A source and API origins are required." }
        require(apiPathPrefixes.isNotEmpty() && apiPathPrefixes.all { it.startsWith('/') && it.none { c -> c in "?%#\\" } }) {
            "Approved API path prefixes are required."
        }
        checkedHeaders(mapOf("User-Agent" to imageUserAgent), IMAGE_HEADERS)
    }

    fun approveApiUrl(value: String): String {
        val url = checkedUrl(value)
        require(url.origin() in approvedApiOrigins && apiPathPrefixes.any { prefix ->
            url.encodedPath == prefix || url.encodedPath.startsWith(if (prefix.endsWith('/')) prefix else "$prefix/")
        }) {
            "API URL is not approved for source $sourceId."
        }
        return url.toString()
    }

    fun approveImageUrl(value: String): String {
        val url = checkedUrl(value)
        require(url.protocol == URLProtocol.HTTPS && url.port == 443 && url.host.lowercase() in approvedImageHosts) {
            "Image host '${url.host}' is not approved for source $sourceId."
        }
        return url.toString()
    }

    fun apiHeaders(headers: Map<String, String>): Map<String, String> = checkedHeaders(headers, apiHeaderNames)

    fun imageHeaders(headers: Map<String, String>): Map<String, String> =
        mapOf("user-agent" to imageUserAgent) + checkedHeaders(headers, IMAGE_HEADERS)

    companion object {
        private val IMAGE_HEADERS = setOf("user-agent", "accept")

        fun checkedImageHost(value: String): String {
            val host = value.trim().lowercase()
            require(host.length <= 253 && host.contains('.') && host.split('.').all { label ->
                label.isNotEmpty() && label.length <= 63 && label.first() != '-' && label.last() != '-' &&
                    label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
            } && host.any { it in 'a'..'z' }) {
                "Image approvals must be exact DNS hostnames, without schemes, wildcards, ports, or paths."
            }
            return host
        }
    }
}

class PackageNetworkPolicy(manifestLocation: String, allowHttp: Boolean = false) {
    val manifestUrl: String
    private val approvedOrigin: String
    private val manifestPath: String
    private val artifactPathPrefix: String

    init {
        val location = checkedUrl(manifestLocation)
        require(location.protocol == URLProtocol.HTTPS ||
            (location.protocol == URLProtocol.HTTP && (allowHttp || location.host.lowercase() in LOOPBACK_HOSTS))
        ) { "Remote plugins require HTTPS or explicit consent to their HTTP source." }
        require(location.encodedQuery.isEmpty()) { "Plugin manifest locations must not contain query parameters." }
        manifestUrl = if (location.encodedPath.endsWith(".wlm")) location.toString() else
            location.toString().trimEnd('/') + "/manifest.wlm"
        val manifest = checkedUrl(manifestUrl)
        approvedOrigin = manifest.origin()
        manifestPath = manifest.encodedPath
        artifactPathPrefix = manifestPath.substringBeforeLast('/') + "/artifacts/sha256/"
    }

    fun approveUrl(value: String): String {
        val url = checkedUrl(value)
        val artifactPath = url.encodedPath.takeIf { it.startsWith(artifactPathPrefix) }?.removePrefix(artifactPathPrefix)
        require(url.origin() == approvedOrigin && url.encodedQuery.isEmpty() &&
            (url.encodedPath == manifestPath || (artifactPath != null && ARTIFACT_PATH.matches(artifactPath) &&
                artifactPath.take(2) == artifactPath.substring(3, 5)))
        ) { "Plugin downloads must stay inside the approved package directory and origin." }
        return url.toString()
    }

    private companion object {
        val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "[::1]", "::1")
        val ARTIFACT_PATH = Regex("[0-9a-f]{2}/[0-9a-f]{64}\\.(wasm|pwasm|cwasm)")
    }
}

private fun checkedUrl(value: String): Url = PluginUrlPolicy.check(value, allowHttp = true)

private fun Url.origin(): String = "${protocol.name}://${host.lowercase()}:$port"

private fun checkedHeaders(headers: Map<String, String>, allowed: Set<String>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    for ((name, value) in headers) {
        val normalized = name.lowercase()
        require(normalized in allowed && normalized !in result) { "HTTP header '$name' is not approved or is duplicated." }
        require(value.length <= 1024 && value.none { it < ' ' || it == '\u007f' }) { "Invalid HTTP header value." }
        result[normalized] = value
    }
    return result
}
