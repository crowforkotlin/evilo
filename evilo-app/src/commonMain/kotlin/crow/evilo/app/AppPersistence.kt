package crow.evilo.app

import androidx.compose.runtime.Composable
import crow.evilo.storage.SnapshotPersistence

@Composable
internal expect fun rememberAppPersistence(): SnapshotPersistence
