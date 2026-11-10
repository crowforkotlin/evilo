package crow.evilo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import crow.evilo.storage.FileSnapshotPersistence
import crow.evilo.storage.SnapshotPersistence
import okio.FileSystem
import okio.Path.Companion.toPath

@Composable
internal actual fun rememberAppPersistence(): SnapshotPersistence {
    val context = LocalContext.current.applicationContext
    return remember(context) { FileSnapshotPersistence(FileSystem.SYSTEM, context.filesDir.resolve("state/evilo.json").absolutePath.toPath()) }
}
