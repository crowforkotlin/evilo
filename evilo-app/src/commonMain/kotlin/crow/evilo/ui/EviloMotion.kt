package crow.evilo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal object EviloMotion {
    const val PageDurationMillis = 280
    const val ComponentDurationMillis = 180
    const val ExitDurationMillis = 120
    const val SlowDurationMillis = 360
    const val ToastDurationMillis = 5_000L
    val Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

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
    val distance = with(LocalDensity.current) { EviloSpacing.ExtraLarge.roundToPx() }
    val layoutDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val localState = remember { SeekableTransitionState(targetState) }
    val transitionState = backState?.transition ?: localState
    val previewing = backState?.previewing == true
    val transition = rememberTransition(transitionState, label)
    LaunchedEffect(targetState, previewing) {
        if (!previewing) transitionState.animateTo(targetState)
    }
    transition.AnimatedContent(
        modifier = modifier.fillMaxSize().clipToBounds(),
        contentKey = contentKey,
        transitionSpec = {
            val change = depth(this.targetState).compareTo(depth(initialState))
            val transform = when {
                contentKey(initialState) == contentKey(this.targetState) -> EnterTransition.None togetherWith ExitTransition.None
                change == 0 -> fadeIn(EviloMotion.spatialSpec()) togetherWith fadeOut(EviloMotion.exitSpec())
                else -> {
                    val direction = change * layoutDirection
                    (fadeIn(EviloMotion.spatialSpec()) + slideInHorizontally(EviloMotion.spatialSpec()) { direction * distance }) togetherWith
                        (fadeOut(EviloMotion.exitSpec()) + slideOutHorizontally(EviloMotion.spatialSpec()) { -direction * distance / 3 })
                }
            }
            transform.using(null)
        },
    ) { displayed ->
        val active = parentActive && !previewing && contentKey(displayed) == contentKey(targetState)
        CompositionLocalProvider(LocalEviloPageActive provides active) {
            Box(Modifier.fillMaxSize().eviloTransitionInput(active), contentAlignment = Alignment.TopCenter) { content(displayed) }
        }
    }
}

@Composable
internal fun <T> EviloContentTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it },
    label: String = "content",
    content: @Composable (T) -> Unit,
) {
    val parentActive = LocalEviloPageActive.current
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        contentKey = contentKey,
        label = label,
        transitionSpec = {
            val transform = if (contentKey(initialState) == contentKey(targetState)) EnterTransition.None togetherWith ExitTransition.None
                else fadeIn(EviloMotion.componentSpec()) togetherWith fadeOut(EviloMotion.exitSpec())
            transform.using(null)
        },
    ) { displayed ->
        val active = parentActive && contentKey(displayed) == contentKey(targetState)
        CompositionLocalProvider(LocalEviloPageActive provides active) {
            Box(Modifier.eviloTransitionInput(active)) { content(displayed) }
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
