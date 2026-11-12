package crow.evilo.app

import androidx.compose.runtime.Composable
import kotlin.native.Platform

@Composable
internal actual fun platformDebugDiagnostics(): Boolean = Platform.isDebugBinary
