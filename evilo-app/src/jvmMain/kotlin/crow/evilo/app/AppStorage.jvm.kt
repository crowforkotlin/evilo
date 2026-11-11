package crow.evilo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.core.okio.OkioStorage
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import crow.evilo.storage.EviloDatabase
import crow.evilo.storage.EviloDatabaseConstructor
import java.io.File
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.Path.Companion.toPath

@Composable
internal actual fun rememberAppStorageFactory(): AppStorageFactory = remember {
    AppStorageFactory { scope ->
        val home = System.getProperty("user.home")
        val directory = when {
            System.getProperty("os.name").startsWith("Windows", true) -> File(System.getenv("APPDATA") ?: home, "evilo")
            System.getProperty("os.name").startsWith("Mac", true) -> File(home, "Library/Application Support/evilo")
            else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, "evilo")
        }.absolutePath.toPath()
        FileSystem.SYSTEM.createDirectories(directory)
        val database = Room.databaseBuilder<EviloDatabase>((directory / "evilo.catalogs.db").toString(), EviloDatabaseConstructor::initialize)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()
        createAppStorage(OkioStorage(FileSystem.SYSTEM, EviloPreferencesSerializer,
            producePath = { directory / "evilo.preferences.pb" }), database, scope)
    }
}
