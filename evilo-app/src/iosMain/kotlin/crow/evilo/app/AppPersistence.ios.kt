@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package crow.evilo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import crow.evilo.storage.FileSnapshotPersistence
import crow.evilo.storage.SnapshotPersistence
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@Composable
internal actual fun rememberAppPersistence(): SnapshotPersistence = remember {
    val directory = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
    FileSnapshotPersistence(FileSystem.SYSTEM, "$directory/evilo/state.json".toPath())
}
