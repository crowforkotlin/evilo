@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package crow.evilo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.core.okio.OkioStorage
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import crow.evilo.storage.EviloDatabase
import crow.evilo.storage.EviloDatabaseConstructor
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@Composable
internal actual fun rememberAppStorageFactory(): AppStorageFactory = remember {
    AppStorageFactory { scope ->
        val applicationSupport = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
        val directory = "$applicationSupport/evilo".toPath()
        FileSystem.SYSTEM.createDirectories(directory)
        val database = Room.databaseBuilder<EviloDatabase>((directory / "evilo.catalogs.db").toString(), EviloDatabaseConstructor::initialize)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()
        createAppStorage(OkioStorage(FileSystem.SYSTEM, EviloPreferencesSerializer,
            producePath = { directory / "evilo.preferences.pb" }), database, scope)
    }
}
