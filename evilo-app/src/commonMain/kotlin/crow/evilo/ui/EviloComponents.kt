package crow.evilo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import crow.evilo.app.resources.Res
import crow.evilo.app.resources.home_clear_search
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EviloSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSearch: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        shape = CircleShape,
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = { Text(hint, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(EviloIcons.Search, null) },
        trailingIcon = {
            AnimatedVisibility(value.isNotEmpty(),
                enter = fadeIn(EviloMotion.componentSpec()) + scaleIn(EviloMotion.componentSpec(), initialScale = 0.92f),
                exit = fadeOut(EviloMotion.exitSpec()) + scaleOut(EviloMotion.exitSpec(), targetScale = 0.92f)) {
                IconButton(onClick = { onValueChange("") }, enabled = enabled && value.isNotEmpty()) {
                    Icon(EviloIcons.Close, stringResource(Res.string.home_clear_search))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke(); focus.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerLow,
            unfocusedContainerColor = colors.surfaceContainerLow,
            disabledContainerColor = colors.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
internal fun EviloSectionHeading(title: String, description: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        description?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun EviloEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(EviloLayout.BorderWidth, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(EviloSpacing.ExtraLarge),
            verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(EviloSpacing.Medium).size(EviloLayout.IconSize),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            action()
        }
    }
}

@Composable
internal fun EviloSettingContent(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(EviloSpacing.Large),
        horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(EviloLayout.IconSize), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailingContent?.invoke()
    }
}

@Composable
internal fun EviloSettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(EviloLayout.BorderWidth, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, Modifier.padding(start = EviloSpacing.Large, end = EviloSpacing.Large, top = EviloSpacing.Large),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
