package crow.evilo.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import crow.evilo.app.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "evilo",
        state = rememberWindowState(width = 1080.dp, height = 800.dp),
    ) {
        App()
    }
}
