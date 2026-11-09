package crow.evilo.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import crow.evilo.feature.library.LibraryScreen
import crow.evilo.feature.library.LibraryUiState

@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LibraryScreen(
                state = LibraryUiState(),
                modifier = Modifier.safeDrawingPadding(),
            )
        }
    }
}
