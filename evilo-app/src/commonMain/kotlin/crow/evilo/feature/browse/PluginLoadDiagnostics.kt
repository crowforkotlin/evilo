package crow.evilo.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import crow.evilo.app.LocalAppDiagnostics
import crow.evilo.app.PluginLoadTiming
import crow.evilo.app.resources.*
import crow.evilo.ui.EviloSpacing
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PluginLoadDiagnostics(timings: List<PluginLoadTiming>) {
    if (!LocalAppDiagnostics.current || timings.isEmpty()) return
    val current = timings.last()
    var runningMs by remember(current) { mutableStateOf(current.started.elapsedNow().inWholeMilliseconds) }
    LaunchedEffect(current) {
        if (current.finishedAtMs == null) {
            while (true) {
                runningMs = current.started.elapsedNow().inWholeMilliseconds
                delay(100)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
        Text(stringResource(Res.string.plugin_debug_timings), style = MaterialTheme.typography.titleSmall)
        timings.forEach { timing ->
            val elapsed = timing.finishedAtMs?.minus(timing.startedAtMs) ?: runningMs
            Text(stringResource(Res.string.plugin_debug_timing, pluginDebugPhaseLabel(timing.phase), elapsed),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        current.finishedAtMs?.let {
            Text(stringResource(Res.string.plugin_debug_total, it), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(Res.string.plugin_debug_timing_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PluginLoadDiagnosticsDialog(timings: List<PluginLoadTiming>, onDismiss: () -> Unit) {
    if (!LocalAppDiagnostics.current) return
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(Res.string.plugin_debug_timings)) },
        text = { SelectionContainer {
            Column(Modifier.verticalScroll(rememberScrollState())) { PluginLoadDiagnostics(timings) }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.home_dismiss)) } })
}
