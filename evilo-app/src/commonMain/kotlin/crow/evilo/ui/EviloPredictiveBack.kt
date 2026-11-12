package crow.evilo.ui

import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.PredictiveBackHandler
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive

/** UI-only seeking: the owning store is changed only after a completed back gesture. */
internal class EviloBackTransitionState<T>(initialState: T) {
    val transition = SeekableTransitionState(initialState)
    var previewing by mutableStateOf(false)
        private set

    suspend fun handle(
        progress: Flow<Float>,
        current: T,
        previous: T?,
        isCurrent: () -> Boolean,
        onBack: () -> Unit,
    ) {
        if (!isCurrent()) {
            progress.collect { }
            return
        }
        previewing = previous != null
        try {
            if (previous != null) transition.snapTo(current)
            progress.collect { fraction ->
                if (previous != null) transition.seekTo(fraction.coerceIn(0f, 1f), previous)
            }
            if (isCurrent()) {
                if (previous != null) transition.animateTo(previous)
                // Recheck after settling: uninstalls or other navigation may have replaced the page.
                if (isCurrent()) onBack()
            }
        } catch (cancelled: CancellationException) {
            // A cancelled event flow need not cancel this coroutine. Do not animate after disposal.
            if (previous != null && currentCoroutineContext().isActive) transition.animateTo(current)
            throw cancelled
        } finally {
            previewing = false
        }
    }
}

@Composable
internal fun <T> rememberEviloBackTransition(initialState: T): EviloBackTransitionState<T> =
    remember { EviloBackTransitionState(initialState) }

@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun <T> EviloPredictiveBackHandler(
    enabled: Boolean,
    state: EviloBackTransitionState<T>,
    current: T,
    previous: T?,
    navigationKey: Any?,
    onBack: () -> Unit,
) {
    val latestCurrent by rememberUpdatedState(current)
    val latestPrevious by rememberUpdatedState(previous)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestKey by rememberUpdatedState(navigationKey)
    val latestOnBack by rememberUpdatedState(onBack)
    // Keep the callback identity stable: desktop/iOS compatibility handlers remember it as a key.
    val handler: suspend (Flow<Float>) -> Unit = remember(state) {
        { progress ->
            val startedKey = latestKey
            val startedCurrent = latestCurrent
            val startedPrevious = latestPrevious
            state.handle(progress, startedCurrent, startedPrevious,
                isCurrent = { latestEnabled && latestKey == startedKey }, onBack = { latestOnBack() })
        }
    }
    if (!LocalInspectionMode.current) PredictiveBackHandler(
        enabled = enabled,
        // Infer the platform event type here; Android's compatibility typealias is internal.
        onBack = remember(handler) {
            { progress -> handler(progress.map { it.progress }) }
        },
    )
}
