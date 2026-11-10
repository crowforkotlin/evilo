package crow.evilo.network.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Authenticator
import okhttp3.CookieJar

actual val supportsLocalPluginManifests: Boolean = true

actual fun createEviloHttpClient(): HttpClient = HttpClient(OkHttp) {
    configureEviloHttp()
    engine {
        config {
            followRedirects(false)
            followSslRedirects(false)
            cookieJar(CookieJar.NO_COOKIES)
            authenticator(Authenticator.NONE)
            proxyAuthenticator(Authenticator.NONE)
        }
    }
}
