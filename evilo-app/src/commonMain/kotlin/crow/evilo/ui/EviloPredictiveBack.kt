package crow.evilo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.PredictiveBackHandler
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sign

/** Platform-neutral event fields; Android's compatibility event typealias is internal. */
internal data class EviloBackEvent(val progress: Float, val touchY: Float = 0f, val fromRight: Boolean = false)

internal data class EviloBackGeometry(val scale: Float, val x: Float, val y: Float, val radius: Float, val alpha: Float)

/** Gesture-following page motion in physical pixels; commit continues toward the swipe edge. */
internal object EviloPredictiveMotion {
    const val CommitMillis = 260
    const val CancelMillis = 220
    private val easeOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    fun geometry(progress: Float, dragY: Float, fromRight: Boolean, width: Float, height: Float,
        density: Float, commit: Float = 0f): EviloBackGeometry {
        val p = progress.coerceIn(0f, 1f)
        val c = commit.coerceIn(0f, 1f)
        val xShift = (width * 0.06f).coerceIn(0f, 28f * density)
        val yMax = (height * 0.025f).coerceIn(0f, 20f * density)
        val y = if (height > 0f) easeOut.transform((abs(dragY) / height).coerceIn(0f, 1f)) * sign(dragY) * yMax else 0f
        val direction = if (fromRight) -1f else 1f
        val dragX = xShift * p
        val commitX = (width * 0.14f).coerceAtLeast(dragX)
        return EviloBackGeometry(
            scale = 1f - 0.06f * p,
            x = direction * (dragX + (commitX - dragX) * c),
            y = y * p * (1f - c),
            radius = 24f * density * p,
            alpha = 1f - c,
        )
    }
}

/** The store is changed only after completion; both pages stay composed during a gesture. */
internal class EviloBackTransitionState<T>(initialState: T, private val contentKey: (T) -> Any?) {
    private val payloads = EviloTransitionPayloadCache(initialState, contentKey)
    val transition = SeekableTransitionState(payloads.target(initialState))

    // Recomposition during a gesture must not overwrite either captured page.
    fun target(value: T): EviloTransitionPayload<T> =
        if (previewing) checkNotNull(capturedTarget) else payloads.target(value)
    var previewing by mutableStateOf(false)
        private set
    var outgoing: T? by mutableStateOf(null)
        private set
    private var capturedTarget: EviloTransitionPayload<T>? = null
    var seekSharedElements by mutableStateOf(false)
        private set
    var event by mutableStateOf(EviloBackEvent(0f))
        private set
    var startY by mutableStateOf(0f)
        private set
    var commitFraction by mutableStateOf(0f)
        private set

    suspend fun handle(progress: Flow<EviloBackEvent>, current: T, previous: T?, isCurrent: () -> Boolean, onBack: () -> Unit,
        awaitNavigation: suspend () -> Unit = {}, sharedElements: Boolean = false) {
        if (!isCurrent()) { progress.collect { }; return }
        val currentTarget = target(current)
        val previousTarget: EviloTransitionPayload<T>? = previous?.let { EviloTransitionPayload<T>(it, contentKey(it)) }
        var started = false
        var committing = false
        try {
            progress.collect { next ->
                if (previousTarget != null) {
                    if (!started) {
                        started = true
                        startY = next.touchY
                        outgoing = current
                        capturedTarget = currentTarget
                        seekSharedElements = sharedElements
                        previewing = true
                        transition.snapTo(currentTarget)
                        // Mount the real destination without playing the ordinary slide/fade.
                        transition.seekTo(0f, previousTarget)
                    }
                    event = next.copy(progress = next.progress.coerceIn(0f, 1f))
                    if (seekSharedElements) transition.seekTo(event.progress, previousTarget)
                }
            }
            if (isCurrent()) {
                if (started) {
                    if (seekSharedElements && previousTarget != null) {
                        transition.animateTo(previousTarget,
                            tween(EviloPredictiveMotion.CommitMillis, easing = EviloMotion.SettleEasing))
                        commitFraction = 1f
                    } else {
                        Animatable(0f).animateTo(1f, tween(EviloPredictiveMotion.CommitMillis, easing = EviloMotion.SettleEasing)) {
                            commitFraction = value
                        }
                    }
                }
                if (isCurrent()) {
                    if (started && previousTarget != null) {
                        transition.snapTo(previousTarget)
                        payloads.adopt(previousTarget)
                        committing = true
                    }
                    onBack()
                    if (started) awaitNavigation()
                } else if (started) {
                    transition.snapTo(currentTarget)
                }
            }
        } catch (cancelled: CancellationException) {
            if (started && !committing && currentCoroutineContext().isActive) {
                if (seekSharedElements) {
                    transition.animateTo(currentTarget,
                        tween(EviloPredictiveMotion.CancelMillis, easing = EviloMotion.SettleEasing))
                } else {
                    val cancelledEvent = event
                    Animatable(1f).animateTo(0f, tween(EviloPredictiveMotion.CancelMillis, easing = EviloMotion.SettleEasing)) {
                        event = cancelledEvent.copy(progress = cancelledEvent.progress * value,
                            touchY = startY + (cancelledEvent.touchY - startY) * value)
                    }
                }
                transition.snapTo(currentTarget)
            }
            throw cancelled
        } finally {
            previewing = false
            outgoing = null
            capturedTarget = null
            seekSharedElements = false
            commitFraction = 0f
            event = EviloBackEvent(0f)
        }
    }
}

@Composable
internal fun <T> rememberEviloBackTransition(initialState: T, contentKey: (T) -> Any?): EviloBackTransitionState<T> =
    remember { EviloBackTransitionState(initialState, contentKey) }

@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun <T> EviloPredictiveBackHandler(enabled: Boolean, state: EviloBackTransitionState<T>, current: T,
    previous: T?, navigationKey: Any?, onBack: () -> Unit, sharedElements: Boolean = false) {
    val latestCurrent by rememberUpdatedState(current)
    val latestPrevious by rememberUpdatedState(previous)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestKey by rememberUpdatedState(navigationKey)
    val latestOnBack by rememberUpdatedState(onBack)
    val latestSharedElements by rememberUpdatedState(sharedElements)
    val handler: suspend (Flow<EviloBackEvent>) -> Unit = remember(state) {
        { progress ->
            val startedKey = latestKey
            state.handle(progress, latestCurrent, latestPrevious,
                isCurrent = { latestEnabled && latestKey == startedKey }, onBack = { latestOnBack() },
                awaitNavigation = {
                    // Keep the preview mounted until collection observes the real destination.
                    // A blocked Back must not leave input disabled indefinitely.
                    withTimeoutOrNull(1_000) {
                        snapshotFlow { latestKey }.first { it != startedKey }
                        withFrameNanos { }
                    }
                }, sharedElements = latestSharedElements)
        }
    }
    if (!LocalInspectionMode.current) PredictiveBackHandler(enabled = enabled, onBack = remember(handler) {
        { progress -> handler(progress.map { EviloBackEvent(it.progress, it.touchY, it.swipeEdge == 1) }) }
    })
}
