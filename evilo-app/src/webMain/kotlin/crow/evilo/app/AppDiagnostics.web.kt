package crow.evilo.app

import androidx.compose.runtime.Composable

// Browser launchers can explicitly opt in with WebApp(debugDiagnostics = true).
@Composable
internal actual fun platformDebugDiagnostics(): Boolean = false
