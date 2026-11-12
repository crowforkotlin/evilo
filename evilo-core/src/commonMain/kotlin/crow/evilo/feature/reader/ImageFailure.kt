package crow.evilo.feature.reader

import crow.evilo.network.ktor.HttpImageContentException
import crow.evilo.network.ktor.HttpNetworkException
import crow.evilo.network.ktor.HttpOpaqueResponseException
import crow.evilo.network.ktor.HttpRedirectRejectedException
import crow.evilo.network.ktor.HttpResponseLimitException
import crow.evilo.network.ktor.HttpStatusException
import crow.evilo.network.ktor.HttpTransportTimeoutException
import crow.evilo.network.ktor.NetworkPolicyException
import crow.evilo.network.ktor.NetworkPolicyViolation
import crow.evilo.network.ktor.PluginUrlPolicy
import kotlinx.coroutines.CancellationException

enum class ImageFailureKind { INVALID_ADDRESS, NETWORK, HTTP, PERMISSION, FORMAT, LIMIT, SESSION_CLOSED, UNKNOWN }

data class ImageFailure(
    val kind: ImageFailureKind,
    val redactedAddress: String? = null,
    val httpStatus: Int? = null,
    val redirectRejected: Boolean = false,
    val maxBytes: Long? = null,
    val maxDimension: Int? = null,
    val maxPixels: Long? = null,
    val policyViolation: NetworkPolicyViolation? = null,
    val rejectedHeader: String? = null,
) {
    val canRetry get() = kind in setOf(ImageFailureKind.NETWORK, ImageFailureKind.HTTP, ImageFailureKind.UNKNOWN) && !redirectRejected
}

class PluginSessionClosedException : IllegalStateException("This plugin session is closed.")
class PluginImageFormatException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
class PluginImageLimitException(val maxBytes: Long? = null, val maxDimension: Int? = null, val maxPixels: Long? = null) :
    IllegalStateException("Image exceeds its safety limit.")

/** Only typed fields leave the request boundary; exception messages and header values are never displayed or copied. */
fun imageFailure(failure: Throwable, url: String?): ImageFailure? {
    val causes = generateSequence(failure) { it.cause }.take(16).toList()
    if (causes.any { it is CancellationException }) return null
    val details = causes.firstNotNullOfOrNull { cause -> when (cause) {
        is PluginSessionClosedException -> ImageFailure(ImageFailureKind.SESSION_CLOSED)
        is NetworkPolicyException -> ImageFailure(if (cause.violation in setOf(NetworkPolicyViolation.INVALID_URL, NetworkPolicyViolation.URL_PATH))
            ImageFailureKind.INVALID_ADDRESS else ImageFailureKind.PERMISSION,
            policyViolation = cause.violation,
            rejectedHeader = cause.rejectedHeader?.takeIf { it.isNotEmpty() && it.length <= 64 && it.all { character ->
                character in 'a'..'z' || character in '0'..'9' || character in "!#$%&'*+.^_`|~-"
            } })
        is HttpRedirectRejectedException -> ImageFailure(ImageFailureKind.HTTP, httpStatus = cause.statusCode, redirectRejected = true)
        is HttpStatusException -> ImageFailure(ImageFailureKind.HTTP, httpStatus = cause.statusCode)
        is HttpResponseLimitException -> ImageFailure(ImageFailureKind.LIMIT, httpStatus = cause.statusCode, maxBytes = cause.maxBytes)
        is HttpNetworkException, is HttpTransportTimeoutException, is HttpOpaqueResponseException -> ImageFailure(ImageFailureKind.NETWORK)
        is HttpImageContentException, is PluginImageFormatException -> ImageFailure(ImageFailureKind.FORMAT)
        is PluginImageLimitException -> ImageFailure(ImageFailureKind.LIMIT, maxBytes = cause.maxBytes,
            maxDimension = cause.maxDimension, maxPixels = cause.maxPixels)
        else -> null
    } } ?: ImageFailure(ImageFailureKind.UNKNOWN)
    return details.copy(redactedAddress = redactedImageAddress(url))
}

fun redactedImageAddress(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val url = try { PluginUrlPolicy.check(value, allowHttp = true) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { return null }
    if (!url.host.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in ".-:[]" }) return null
    // A path can also contain a bearer token, so diagnostics retain only the origin.
    return "${PluginUrlPolicy.origin(url)}/..."
}
