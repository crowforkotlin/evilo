package crow.evilo.network.ktor

enum class NetworkPolicyViolation {
    INVALID_URL, CLEARTEXT_NOT_APPROVED, URL_CREDENTIALS, URL_PATH,
    ORIGIN_OR_PATH_NOT_APPROVED, METHOD_NOT_APPROVED, REQUEST_BODY, HEADERS_NOT_APPROVED,
}

class NetworkPolicyException(val violation: NetworkPolicyViolation, message: String, cause: Throwable? = null,
    val rejectedHeader: String? = null) : IllegalArgumentException(message, cause)

internal inline fun requireNetwork(value: Boolean, violation: NetworkPolicyViolation,
    rejectedHeader: String? = null, message: () -> String) {
    if (!value) throw NetworkPolicyException(violation, message(), rejectedHeader = rejectedHeader)
}

sealed class HttpTransportException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

class HttpStatusException(val statusCode: Int) : HttpTransportException("Image request failed: HTTP $statusCode.")
class HttpRedirectRejectedException(val statusCode: Int) : HttpTransportException("HTTP redirects are disabled: HTTP $statusCode.")
class HttpOpaqueResponseException : HttpTransportException("An opaque browser response was rejected.")
class HttpResponseLimitException(val maxBytes: Long, val statusCode: Int) :
    HttpTransportException("HTTP response exceeds the $maxBytes byte limit.")
class HttpTransportTimeoutException(val timeoutMillis: Long) : HttpTransportException("HTTP request timed out after $timeoutMillis ms.")
class HttpNetworkException(cause: Throwable) : HttpTransportException("Network request failed.", cause)
class HttpImageContentException : HttpTransportException("The response is not image content.")
