package crow.evilo.feature.reader

import crow.evilo.network.ktor.HttpNetworkException
import crow.evilo.network.ktor.HttpOpaqueResponseException
import crow.evilo.network.ktor.HttpRedirectRejectedException
import crow.evilo.network.ktor.HttpResponseLimitException
import crow.evilo.network.ktor.HttpStatusException
import crow.evilo.network.ktor.NetworkPolicyException
import crow.evilo.network.ktor.NetworkPolicyViolation
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageFailureTest {
    @Test
    fun failureCategoriesUseTypesNotExceptionText() {
        assertEquals(ImageFailureKind.HTTP, imageFailure(HttpStatusException(403), URL)?.kind)
        assertEquals(403, imageFailure(HttpStatusException(403), URL)?.httpStatus)
        assertEquals(ImageFailureKind.UNKNOWN, imageFailure(IllegalStateException("HTTP 403 permission denied"), URL)?.kind)
        assertEquals(ImageFailureKind.PERMISSION, imageFailure(NetworkPolicyException(NetworkPolicyViolation.HEADERS_NOT_APPROVED, "Header denied."), URL)?.kind)
        assertEquals(ImageFailureKind.INVALID_ADDRESS, imageFailure(NetworkPolicyException(NetworkPolicyViolation.INVALID_URL, "URL denied."), URL)?.kind)
        assertEquals(ImageFailureKind.FORMAT, imageFailure(PluginImageFormatException("Malformed header."), URL)?.kind)
        assertEquals(ImageFailureKind.LIMIT, imageFailure(PluginImageLimitException(maxDimension = 512, maxPixels = 262_144), URL)?.kind)
        assertEquals(ImageFailureKind.SESSION_CLOSED, imageFailure(PluginSessionClosedException(), URL)?.kind)
    }

    @Test
    fun diagnosticValuesNeverContainPathsQueriesCredentialsOrExceptionMessages() {
        val failure = assertNotNull(imageFailure(HttpNetworkException(IllegalStateException("Authorization: bearer private-value Cookie: secret")),
            "https://images.example.com/private-path-token/cover.png?signature=private-query#fragment"))
        assertNull(failure.redactedAddress)
        val signed = assertNotNull(imageFailure(HttpStatusException(404), "https://images.example.com/private-path-token/cover.png?signature=private-query"))
        assertEquals("https://images.example.com:443/...", signed.redactedAddress)
        val diagnostic = signed.toString() + failure.toString()
        for (secret in listOf("private-path-token", "private-query", "private-value", "Cookie", "fragment")) assertFalse(secret in diagnostic)
        assertNull(redactedImageAddress("https://user:credential@images.example.com/cover.png"))
        assertNull(redactedImageAddress("   "))
        assertNull(redactedImageAddress("/relative/image.png"))
    }

    @Test
    fun policyDiagnosticsKeepOnlyTheTypedReasonAndHeaderName() {
        val failure = assertNotNull(imageFailure(NetworkPolicyException(NetworkPolicyViolation.HEADERS_NOT_APPROVED,
            "Authorization: bearer private-value", rejectedHeader = "authorization"), URL))
        assertEquals(NetworkPolicyViolation.HEADERS_NOT_APPROVED, failure.policyViolation)
        assertEquals("authorization", failure.rejectedHeader)
        assertFalse("private-value" in failure.toString())
        val invalidHeader = assertNotNull(imageFailure(NetworkPolicyException(NetworkPolicyViolation.HEADERS_NOT_APPROVED,
            "Ignored", rejectedHeader = "cookie: secret"), URL))
        assertNull(invalidHeader.rejectedHeader)
        val origin = assertNotNull(imageFailure(NetworkPolicyException(NetworkPolicyViolation.ORIGIN_OR_PATH_NOT_APPROVED,
            "Ignored"), URL))
        assertEquals(NetworkPolicyViolation.ORIGIN_OR_PATH_NOT_APPROVED, origin.policyViolation)
        assertEquals(ImageFailureKind.PERMISSION, origin.kind)
    }

    @Test
    fun wrappedCancellationIsNeverAnOrdinaryImageError() {
        assertNull(imageFailure(CancellationException("Leaving reader."), URL))
        assertNull(imageFailure(IllegalStateException("Wrapper", CancellationException("Leaving reader.")), URL))
    }

    @Test
    fun redirectsAndOpaqueResponsesDoNotPretendToBeMissingImagesOrConfirmedCors() {
        val redirect = assertNotNull(imageFailure(HttpRedirectRejectedException(302), URL))
        assertEquals(302, redirect.httpStatus)
        assertTrue(redirect.redirectRejected)
        assertFalse(redirect.canRetry)
        assertEquals(ImageFailureKind.NETWORK, imageFailure(HttpOpaqueResponseException(), URL)?.kind)
        assertTrue(assertNotNull(imageFailure(HttpStatusException(503), URL)).canRetry)
        assertFalse(ImageFailure(ImageFailureKind.PERMISSION).canRetry)
        assertFalse(ImageFailure(ImageFailureKind.FORMAT).canRetry)
        assertFalse(ImageFailure(ImageFailureKind.LIMIT).canRetry)
        assertFalse(ImageFailure(ImageFailureKind.SESSION_CLOSED).canRetry)
    }

    @Test
    fun boundedHttpFailuresKeepTheRealStatusAndLimit() {
        val failure = assertNotNull(imageFailure(HttpResponseLimitException(1024, 403), URL))
        assertEquals(ImageFailureKind.LIMIT, failure.kind)
        assertEquals(403, failure.httpStatus)
        assertEquals(1024L, failure.maxBytes)
    }

    private companion object {
        const val URL = "https://images.example.com/cover.png"
    }
}
