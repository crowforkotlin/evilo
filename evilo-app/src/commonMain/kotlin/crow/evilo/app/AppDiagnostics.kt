package crow.evilo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalAppDiagnostics = staticCompositionLocalOf { false }

@Composable
internal expect fun platformDebugDiagnostics(): Boolean
