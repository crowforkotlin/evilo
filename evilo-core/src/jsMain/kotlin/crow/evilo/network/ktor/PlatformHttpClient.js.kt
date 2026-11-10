package crow.evilo.network.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual val supportsLocalPluginManifests: Boolean = false

actual fun createEviloHttpClient(): HttpClient = HttpClient(Js) {
    configureEviloHttp()
    engine {
        configureRequest {
            credentials = "omit"
            redirect = "error"
            referrerPolicy = "no-referrer"
        }
    }
}
