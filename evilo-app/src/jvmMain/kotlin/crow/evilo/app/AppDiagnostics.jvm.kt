package crow.evilo.app

import androidx.compose.runtime.Composable

@Composable
internal actual fun platformDebugDiagnostics(): Boolean = java.lang.Boolean.getBoolean("evilo.debugDiagnostics")
