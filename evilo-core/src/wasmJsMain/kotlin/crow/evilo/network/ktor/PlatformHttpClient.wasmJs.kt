package crow.evilo.network.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlin.js.toJsString

actual val supportsLocalPluginManifests: Boolean = false

actual fun createEviloHttpClient(): HttpClient = HttpClient(Js) {
    configureEviloHttp()
    engine {
        configureRequest {
            credentials = "omit".toJsString()
            redirect = "error".toJsString()
            referrerPolicy = "no-referrer".toJsString()
        }
    }
}
