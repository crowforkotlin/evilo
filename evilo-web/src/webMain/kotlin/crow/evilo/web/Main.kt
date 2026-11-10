package crow.evilo.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import crow.evilo.app.WebApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        WebApp()
    }
}
