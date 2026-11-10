package crow.evilo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import crow.evilo.storage.FileSnapshotPersistence
import crow.evilo.storage.SnapshotPersistence
import java.io.File
import okio.FileSystem
import okio.Path.Companion.toPath

@Composable
internal actual fun rememberAppPersistence(): SnapshotPersistence = remember {
    val home = System.getProperty("user.home")
    val directory = when {
        System.getProperty("os.name").startsWith("Windows", true) -> File(System.getenv("APPDATA") ?: home, "evilo")
        System.getProperty("os.name").startsWith("Mac", true) -> File(home, "Library/Application Support/evilo")
        else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, "evilo")
    }
    FileSnapshotPersistence(FileSystem.SYSTEM, directory.resolve("state.json").absolutePath.toPath())
}
