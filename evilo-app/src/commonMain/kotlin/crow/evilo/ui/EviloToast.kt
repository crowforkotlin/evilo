package crow.evilo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import crow.evilo.app.resources.Res
import crow.evilo.app.resources.home_dismiss
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

internal class EviloToastMessage(
    val id: Long,
    val message: String,
    val title: String?,
    val error: Boolean,
    val actionLabel: String?,
    val onAction: (() -> Unit)?,
    val onDismiss: (() -> Unit)?,
)

@Stable
internal class EviloToastState {
    var current: EviloToastMessage? by mutableStateOf(null)
        private set
    private var serial = 0L

    fun show(
        message: String,
        title: String? = null,
        error: Boolean = false,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
    ): Long {
        current?.let { dismiss(it.id) }
        val id = ++serial
        current = EviloToastMessage(id, message, title, error, actionLabel, onAction, onDismiss)
        return id
    }

    fun dismiss(id: Long, notify: Boolean = true) {
        val message = current?.takeIf { it.id == id } ?: return
        current = null
        if (notify) message.onDismiss?.invoke()
    }

    fun performAction(id: Long) {
        val message = current?.takeIf { it.id == id } ?: return
        dismiss(id, notify = false)
        try {
            message.onAction?.invoke()
        } finally {
            message.onDismiss?.invoke()
        }
    }
}

internal val LocalEviloToastState = staticCompositionLocalOf<EviloToastState> {
    error("EviloToastState must be provided by PluginHome.")
}

@Composable
internal fun EviloToastHost(state: EviloToastState, expanded: Boolean) {
    val message = state.current
    val transition = updateTransition(message, label = "toast")
    val margin = with(LocalDensity.current) { EviloSpacing.Small.roundToPx() }
    LaunchedEffect(message?.id) {
        val id = message?.id ?: return@LaunchedEffect
        delay(EviloMotion.ToastDurationMillis)
        state.dismiss(id)
    }
    if (transition.currentState != null || transition.targetState != null) {
        // Recreate the popup when a dialog raises a new notification.
        key(message?.id ?: transition.currentState?.id) {
            Popup(
                alignment = if (expanded) Alignment.BottomEnd else Alignment.BottomCenter,
                offset = IntOffset(0, -margin),
                properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
            ) {
                transition.AnimatedContent(
                    modifier = Modifier.widthIn(max = EviloLayout.ToastMaxWidth),
                    contentAlignment = Alignment.BottomCenter,
                    contentKey = { it?.id },
                    transitionSpec = {
                        val enter = if (targetState == null) EnterTransition.None else
                            fadeIn(EviloMotion.componentSpec()) + slideInVertically(EviloMotion.componentSpec()) { margin }
                        val exit = if (initialState == null) ExitTransition.None else
                            fadeOut(EviloMotion.exitSpec()) + slideOutVertically(EviloMotion.exitSpec()) { margin }
                        (enter togetherWith exit).using(null)
                    },
                ) { displayed ->
                    if (displayed != null) ToastContent(displayed, state)
                }
            }
        }
    }
}

@Composable
private fun ToastContent(message: EviloToastMessage, state: EviloToastState) {
    val colors = MaterialTheme.colorScheme
    val active = state.current?.id == message.id
    val accessibility = if (active) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier.clearAndSetSemantics {}
    Surface(
        modifier = Modifier.fillMaxWidth().padding(EviloSpacing.Small).then(accessibility),
        shape = MaterialTheme.shapes.medium,
        color = if (message.error) colors.errorContainer else colors.inverseSurface,
        contentColor = if (message.error) colors.onErrorContainer else colors.inverseOnSurface,
        shadowElevation = EviloElevation.Floating,
        border = BorderStroke(EviloLayout.BorderWidth, colors.outlineVariant),
    ) {
        Row(Modifier.padding(start = EviloSpacing.Large, end = EviloSpacing.ExtraSmall), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(vertical = EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                message.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                Text(message.message, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                message.actionLabel?.let { label ->
                    TextButton(onClick = { state.performAction(message.id) }, enabled = active,
                        colors = ButtonDefaults.textButtonColors(contentColor = if (message.error) colors.onErrorContainer else colors.inversePrimary)) { Text(label) }
                }
            }
            IconButton(onClick = { state.dismiss(message.id) }, enabled = active, modifier = Modifier.padding(top = EviloSpacing.ExtraSmall)) {
                Icon(EviloIcons.Close, stringResource(Res.string.home_dismiss))
            }
        }
    }
}
