package crow.evilo.plugin.catalog

/**
 * Why a plugin source could not be read. A source failure is user-visible, so every
 * reason keeps the information the user needs to act: the HTTP status, the byte limit,
 * or the malformed field. Without this, every failure collapses into one sentence.
 */
sealed class SourceFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The server answered, but not with a successful status. */
    class HttpStatus(val host: String, val statusCode: Int) : SourceFailure("$host returned HTTP $statusCode.")

    /** The server answered with a redirect, which the host never follows. */
    class Redirect(val statusCode: Int) :
        SourceFailure("The source redirected with HTTP $statusCode; subscribe to the final address instead.")

    /** The response exceeded the catalog byte limit and was cut off. */
    class TooLarge(val maxBytes: Long) :
        SourceFailure("The source document exceeds $maxBytes bytes.")

    /** The host never answered. The address is part of the message so the user can check it. */
    class Unreachable(val host: String, cause: Throwable? = null) :
        SourceFailure("Cannot reach $host.", cause)

    /** A connection was made, but the answer never arrived. */
    class Timeout(val host: String, val millis: Long) :
        SourceFailure("$host did not answer within ${millis / 1000} s.")

    /** The host got a response but it is not JSON. */
    class NotJson(cause: Throwable?) : SourceFailure("The address does not return JSON.", cause)

    /** The document is JSON but not a plugin source, a subscription list, or a repository pointer. */
    class NotACatalog(detail: String) : SourceFailure("The address is not a plugin source: $detail")

    /** The document is well-formed JSON but fails one catalog rule. */
    class Invalid(detail: String) : SourceFailure("The source document is invalid: $detail")

    /** A subscription list named an address that cannot be used. */
    class BadEntry(val index: Int, detail: String) :
        SourceFailure("Subscription entry ${index + 1} is unusable: $detail")

    /** The source's signature was absent or did not verify. */
    class Untrusted(detail: String) : SourceFailure("The source signature is not trusted: $detail")
}
