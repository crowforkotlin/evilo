@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.UnsafeNumber::class)

package crow.evilo.network.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling

actual val supportsLocalPluginManifests: Boolean = true

actual fun createEviloHttpClient(): HttpClient = HttpClient(Darwin) {
    configureEviloHttp()
    engine {
        configureSession {
            HTTPShouldSetCookies = false
            HTTPCookieStorage = null
            URLCredentialStorage = null
            URLCache = null
        }
        handleChallenge { _, _, challenge, completion ->
            completion(
                if (challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust)
                    NSURLSessionAuthChallengePerformDefaultHandling else NSURLSessionAuthChallengeCancelAuthenticationChallenge,
                null,
            )
        }
    }
}
