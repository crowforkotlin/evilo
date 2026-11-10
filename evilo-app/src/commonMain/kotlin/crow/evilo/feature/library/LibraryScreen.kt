package crow.evilo.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crow.evilo.app.resources.Res
import crow.evilo.app.resources.library_empty_description
import crow.evilo.app.resources.library_empty_title
import crow.evilo.app.resources.library_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun LibraryScreen(state: LibraryUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.library_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        if (state.manga.isEmpty()) {
            Text(
                text = stringResource(Res.string.library_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(Res.string.library_empty_description))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.manga) { manga ->
                    Text(manga.title, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
