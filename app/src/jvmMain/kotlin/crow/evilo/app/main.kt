package crow.evilo.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import crow.evilo.app.resources.Res
import crow.evilo.app.resources.app_name
import org.jetbrains.compose.resources.stringResource

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
    ) {
        App()
    }
}
