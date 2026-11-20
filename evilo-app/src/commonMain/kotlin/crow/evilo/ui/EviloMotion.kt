package crow.evilo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal object EviloMotion {
    const val PageDurationMillis = 320
    const val ComponentDurationMillis = 220
    const val ExitDurationMillis = 180
    const val SlowDurationMillis = 420
    const val ToastDurationMillis = 5_000L
    val Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val SettleEasing = CubicBezierEasing(0.16f, 0f, 0.12f, 1f)

    fun <T> spatialSpec(): TweenSpec<T> = tween(PageDurationMillis, easing = Easing)
    fun <T> componentSpec(): TweenSpec<T> = tween(ComponentDurationMillis, easing = Easing)
    fun <T> exitSpec(): TweenSpec<T> = tween(ExitDurationMillis, easing = Easing)

    val Scheme: MotionScheme = object : MotionScheme {
        override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spatialSpec()
        override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = componentSpec()
        override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = tween(SlowDurationMillis, easing = Easing)
        override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = componentSpec()
        override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = exitSpec()
        override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spatialSpec()
    }
}

internal val LocalEviloPageActive = compositionLocalOf { true }
internal val LocalEviloAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
internal fun <T> EviloPageTransition(
    targetState: T,
    contentKey: (T) -> Any?,
    depth: (T) -> Int,
    modifier: Modifier = Modifier,
    label: String = "page",
    backState: EviloBackTransitionState<T>? = null,
    content: @Composable (T) -> Unit,
) {
    val parentActive = LocalEviloPageActive.current
    val distance = with(LocalDensity.current) { 32.dp.roundToPx() }
    val layoutDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val payloads = remember { EviloTransitionPayloadCache(targetState, contentKey) }
    val target = backState?.target(targetState) ?: payloads.target(targetState)
    val localState = remember { SeekableTransitionState(target) }
    val transitionState = backState?.transition ?: localState
    val previewing = backState?.previewing == true
    val transition = rememberTransition(transitionState, label)
    LaunchedEffect(target, previewing) {
        if (!previewing) {
            if (transitionState.currentState.key == target.key) transitionState.snapTo(target)
            else transitionState.animateTo(target)
        }
    }
    transition.AnimatedContent(
        modifier = modifier.fillMaxSize().clipToBounds(),
        contentKey = { it.key },
        transitionSpec = {
            val change = depth(this.targetState.value).compareTo(depth(initialState.value))
            val transform = when {
                // A near-opaque enter keeps a seekable timeline without moving the destination.
                // The graphics layer below owns the outgoing page's gesture motion.
                previewing && backState?.seekSharedElements == true ->
                    (fadeIn(EviloMotion.spatialSpec()) togetherWith
                        (fadeOut(EviloMotion.spatialSpec()) + ExitTransition.KeepUntilTransitionsFinished))
                        .apply { targetContentZIndex = -1f }
                previewing -> (fadeIn(tween(EviloPredictiveMotion.CommitMillis), initialAlpha = 0.9999f) togetherWith
                    ExitTransition.KeepUntilTransitionsFinished).apply { targetContentZIndex = -1f }
                initialState.key == this.targetState.key -> EnterTransition.None togetherWith ExitTransition.None
                change == 0 -> fadeIn(EviloMotion.spatialSpec()) togetherWith
                    (fadeOut(EviloMotion.spatialSpec()) + ExitTransition.KeepUntilTransitionsFinished)
                change > 0 -> {
                    ((fadeIn(EviloMotion.spatialSpec(), initialAlpha = 0.92f) +
                        slideInHorizontally(EviloMotion.spatialSpec()) { layoutDirection * distance }) togetherWith
                        (slideOutHorizontally(EviloMotion.spatialSpec()) { -layoutDirection * distance / 4 } +
                            ExitTransition.KeepUntilTransitionsFinished))
                        .apply { targetContentZIndex = 1f }
                }
                else -> {
                    (slideInHorizontally(EviloMotion.spatialSpec()) { -layoutDirection * distance / 4 } togetherWith
                        (fadeOut(EviloMotion.spatialSpec()) +
                            slideOutHorizontally(EviloMotion.spatialSpec()) { layoutDirection * distance } +
                            ExitTransition.KeepUntilTransitionsFinished))
                        .apply { targetContentZIndex = -1f }
                }
            }
            transform.using(null)
        },
    ) { payload ->
        val displayed = payload.value
        val active = parentActive && !previewing && contentKey(displayed) == contentKey(targetState)
        CompositionLocalProvider(LocalEviloPageActive provides active, LocalEviloAnimatedVisibilityScope provides this) {
            val density = LocalDensity.current.density
            val surface = MaterialTheme.colorScheme.background
            val moving = previewing && backState?.seekSharedElements != true &&
                backState?.outgoing?.let(contentKey) == contentKey(displayed)
            Box(Modifier.fillMaxSize().graphicsLayer {
                if (moving && backState != null) {
                    val motion = EviloPredictiveMotion.geometry(backState.event.progress, backState.event.touchY - backState.startY,
                        backState.event.fromRight, size.width, size.height, density, backState.commitFraction)
                    scaleX = motion.scale
                    scaleY = motion.scale
                    translationX = motion.x
                    translationY = motion.y
                    alpha = motion.alpha
                    shape = RoundedCornerShape((motion.radius / density).dp)
                    clip = true
                } else {
                    scaleX = 1f
                    scaleY = 1f
                    translationX = 0f
                    translationY = 0f
                    alpha = 1f
                    clip = false
                }
            }.background(surface).eviloTransitionInput(active), contentAlignment = Alignment.TopCenter) { content(displayed) }
        }
    }
}

@Composable
internal fun <T> EviloContentTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it },
    label: String = "content",
    direction: (T, T) -> Int = { _, _ -> 0 },
    content: @Composable (T) -> Unit,
) {
    val parentActive = LocalEviloPageActive.current
    val distance = with(LocalDensity.current) { 20.dp.roundToPx() }
    val layoutDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val payloads = remember { EviloTransitionPayloadCache(targetState, contentKey) }
    AnimatedContent(
        targetState = payloads.target(targetState),
        modifier = modifier.clipToBounds(),
        contentKey = { it.key },
        label = label,
        transitionSpec = {
            val slideDirection = direction(initialState.value, this.targetState.value).coerceIn(-1, 1) * layoutDirection
            val transform = when {
                initialState.key == this.targetState.key -> EnterTransition.None togetherWith ExitTransition.None
                slideDirection == 0 -> fadeIn(EviloMotion.componentSpec()) togetherWith
                    (fadeOut(EviloMotion.componentSpec()) + ExitTransition.KeepUntilTransitionsFinished)
                else -> (fadeIn(EviloMotion.componentSpec()) +
                    slideInHorizontally(EviloMotion.componentSpec()) { slideDirection * distance }) togetherWith
                    (fadeOut(EviloMotion.componentSpec()) +
                        slideOutHorizontally(EviloMotion.componentSpec()) { -slideDirection * distance } +
                        ExitTransition.KeepUntilTransitionsFinished)
            }
            transform.using(null)
        },
    ) { payload ->
        val displayed = payload.value
        val active = parentActive && contentKey(displayed) == contentKey(targetState)
        CompositionLocalProvider(LocalEviloPageActive provides active) {
            // Fade the surface with its content instead of drawing two sets of text together.
            Box(Modifier.background(MaterialTheme.colorScheme.background).eviloTransitionInput(active)) { content(displayed) }
        }
    }
}

@Composable
internal fun EviloBarTransition(
    visible: Boolean,
    modifier: Modifier = Modifier,
    alignment: Alignment.Vertical = Alignment.Top,
    content: @Composable () -> Unit,
) {
    // Keep bar animations outside callers' RowScope/ColumnScope receivers.
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.eviloTransitionInput(visible),
        enter = fadeIn(EviloMotion.componentSpec()) +
            expandVertically(EviloMotion.spatialSpec(), expandFrom = alignment),
        exit = fadeOut(EviloMotion.exitSpec()) +
            shrinkVertically(EviloMotion.spatialSpec(), shrinkTowards = alignment),
    ) {
        content()
    }
}

@Composable
internal fun EviloProgressIndicator(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier, enter = fadeIn(EviloMotion.componentSpec()), exit = fadeOut(EviloMotion.exitSpec())) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

internal fun Modifier.eviloTransitionInput(active: Boolean): Modifier = if (active) this else {
    // Outgoing pages remain drawn, but cannot receive another navigation action.
    clearAndSetSemantics {}.focusProperties { canFocus = false }.onPreviewKeyEvent { true }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}
