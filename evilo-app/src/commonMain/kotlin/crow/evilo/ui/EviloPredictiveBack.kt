package crow.evilo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.tween
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
import kotlin.math.abs
import kotlin.math.sign

/** Platform-neutral event fields; Android's compatibility event typealias is internal. */
internal data class EviloBackEvent(val progress: Float, val touchY: Float = 0f, val fromRight: Boolean = false)

internal data class EviloBackGeometry(val scale: Float, val x: Float, val y: Float, val radius: Float, val alpha: Float)

/** Flutter 3.44.2's default Android shared-element predictive page motion, in physical pixels. */
internal object EviloPredictiveMotion {
    const val CommitMillis = 400
    const val CancelMillis = 300
    private val easeOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    private val emphasizedStart = CubicBezierEasing(0.05f / 0.166666f, 0f, 0.133333f / 0.166666f, 0.06f / 0.4f)
    private val emphasizedEnd = CubicBezierEasing((0.208333f - 0.166666f) / (1f - 0.166666f), (0.82f - 0.4f) / 0.6f,
        (0.25f - 0.166666f) / (1f - 0.166666f), 1f)
    // Flutter's easeInOutCubicEmphasized is a two-segment cubic, not MD3's ordinary easing.
    val emphasized = Easing { value ->
        if (value < 0.166666f) {
            0.4f * emphasizedStart.transform(value / 0.166666f)
        } else {
            0.4f + 0.6f * emphasizedEnd
                .transform((value - 0.166666f) / (1f - 0.166666f))
        }
    }

    fun geometry(progress: Float, dragY: Float, fromRight: Boolean, width: Float, height: Float,
        density: Float, commit: Float = 0f): EviloBackGeometry {
        val p = progress.coerceIn(0f, 1f)
        val c = commit.coerceIn(0f, 1f)
        val xShift = width / 20f - 8f * density
        val yMax = (height / 20f - 8f * density).coerceAtLeast(0f)
        val y = if (height > 0f) easeOut.transform((abs(dragY) / height).coerceIn(0f, 1f)) * sign(dragY) * yMax else 0f
        val bounce = p * (1f - c)
        return EviloBackGeometry(
            scale = 1f - 0.1f * bounce,
            x = (if (fromRight) -xShift else xShift) * p * (1f - c) + height * 0.1f * c,
            y = y * (1f - c),
            radius = 32f * density * bounce,
            alpha = 1f - c,
        )
    }
}

/** The store is changed only after completion; both pages stay composed during a gesture. */
internal class EviloBackTransitionState<T>(initialState: T) {
    val transition = SeekableTransitionState(initialState)
    var previewing by mutableStateOf(false)
        private set
    var outgoing: T? by mutableStateOf(null)
        private set
    var event by mutableStateOf(EviloBackEvent(0f))
        private set
    var startY by mutableStateOf(0f)
        private set
    var commitFraction by mutableStateOf(0f)
        private set

    suspend fun handle(progress: Flow<EviloBackEvent>, current: T, previous: T?, isCurrent: () -> Boolean, onBack: () -> Unit) {
        if (!isCurrent()) { progress.collect { }; return }
        var started = false
        try {
            progress.collect { next ->
                if (previous != null) {
                    if (!started) {
                        started = true
                        startY = next.touchY
                        outgoing = current
                        previewing = true
                        transition.snapTo(current)
                        // Mount the real destination without playing the ordinary slide/fade.
                        transition.seekTo(0f, previous)
                    }
                    event = next.copy(progress = next.progress.coerceIn(0f, 1f))
                }
            }
            if (isCurrent()) {
                if (started) {
                    Animatable(0f).animateTo(1f, tween(EviloPredictiveMotion.CommitMillis, easing = EviloPredictiveMotion.emphasized)) {
                        commitFraction = value
                    }
                }
                if (isCurrent()) {
                    if (started && previous != null) transition.snapTo(previous)
                    onBack()
                }
            }
        } catch (cancelled: CancellationException) {
            if (started && currentCoroutineContext().isActive) {
                val cancelledEvent = event
                Animatable(1f).animateTo(0f, tween(EviloPredictiveMotion.CancelMillis, easing = EviloMotion.Easing)) {
                    event = cancelledEvent.copy(progress = cancelledEvent.progress * value,
                        touchY = startY + (cancelledEvent.touchY - startY) * value)
                }
                transition.snapTo(current)
            }
            throw cancelled
        } finally {
            previewing = false
            outgoing = null
            commitFraction = 0f
            event = EviloBackEvent(0f)
        }
    }
}

@Composable
internal fun <T> rememberEviloBackTransition(initialState: T): EviloBackTransitionState<T> =
    remember { EviloBackTransitionState(initialState) }

@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun <T> EviloPredictiveBackHandler(enabled: Boolean, state: EviloBackTransitionState<T>, current: T,
    previous: T?, navigationKey: Any?, onBack: () -> Unit) {
    val latestCurrent by rememberUpdatedState(current)
    val latestPrevious by rememberUpdatedState(previous)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestKey by rememberUpdatedState(navigationKey)
    val latestOnBack by rememberUpdatedState(onBack)
    val handler: suspend (Flow<EviloBackEvent>) -> Unit = remember(state) {
        { progress ->
            val startedKey = latestKey
            state.handle(progress, latestCurrent, latestPrevious,
                isCurrent = { latestEnabled && latestKey == startedKey }, onBack = { latestOnBack() })
        }
    }
    if (!LocalInspectionMode.current) PredictiveBackHandler(enabled = enabled, onBack = remember(handler) {
        { progress -> handler(progress.map { EviloBackEvent(it.progress, it.touchY, it.swipeEdge == 1) }) }
    })
}
