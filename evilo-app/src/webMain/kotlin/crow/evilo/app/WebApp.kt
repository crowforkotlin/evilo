package crow.evilo.app

import androidx.compose.runtime.Composable
import crow.evilo.ui.WithEviloFont

@Composable
fun WebApp(debugDiagnostics: Boolean = false) {
    WithEviloFont { ready -> App(debugDiagnostics, contentReady = ready) }
}
