package crow.evilo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import crow.evilo.app.resources.Res
import crow.evilo.app.resources.source_reload
import org.jetbrains.compose.resources.stringResource

/** In-page progress, without replacing navigation or drawing a blocking card. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EviloLoadingState(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = EviloLayout.LoadingStateMinHeight).padding(EviloSpacing.ExtraLarge), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = EviloLayout.PageStateMaxWidth), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
            LoadingIndicator(Modifier.size(EviloLayout.LoadingIndicatorSize), color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

/** Recoverable page failures use the open page surface; cards remain for actual content. */
@Composable
internal fun EviloRetryState(title: String, description: String, onRetry: () -> Unit,
    modifier: Modifier = Modifier, retryLabel: String = stringResource(Res.string.source_reload),
    secondaryAction: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().heightIn(min = EviloLayout.RetryStateMinHeight).padding(EviloSpacing.ExtraLarge), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = EviloLayout.PageStateMaxWidth), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.errorContainer, contentColor = colors.onErrorContainer) {
                Icon(EviloIcons.Refresh, null, Modifier.padding(EviloSpacing.Large).size(EviloLayout.PageStateIconSize))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Button(onClick = onRetry) {
                Icon(EviloIcons.Refresh, null, Modifier.padding(end = EviloSpacing.Small).size(EviloLayout.InlineIconSize))
                Text(retryLabel)
            }
            secondaryAction?.invoke()
        }
    }
}
