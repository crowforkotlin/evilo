package crow.evilo.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloSpacing
import crow.evilo.app.resources.Res
import crow.evilo.app.resources.library_empty_description
import crow.evilo.app.resources.library_empty_title
import crow.evilo.app.resources.library_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun LibraryScreen(state: LibraryUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(EviloLayout.PagePadding),
        verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large),
    ) {
        Text(
            text = stringResource(Res.string.library_title),
            style = MaterialTheme.typography.titleLarge,
        )
        if (state.manga.isEmpty()) {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(EviloLayout.BorderWidth, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(EviloSpacing.ExtraLarge), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                    Text(stringResource(Res.string.library_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.library_empty_description), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                items(state.manga) { manga ->
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(EviloLayout.BorderWidth, MaterialTheme.colorScheme.outlineVariant)) {
                        Text(manga.title, Modifier.padding(EviloSpacing.Large), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
