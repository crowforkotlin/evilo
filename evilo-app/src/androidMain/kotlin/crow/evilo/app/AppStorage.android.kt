package crow.evilo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.okio.OkioStorage
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import crow.evilo.storage.EviloDatabase
import crow.evilo.storage.EviloDatabaseConstructor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import okio.FileSystem
import okio.Path.Companion.toPath

// Kept outside the composition so consecutive Activity instances share the file lease.
private val androidStorageAccess = Mutex()

@Composable
internal actual fun rememberAppStorageFactory(): AppStorageFactory {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        AppStorageFactory { scope ->
            val database = Room.databaseBuilder<EviloDatabase>(context, "evilo.catalogs.db", EviloDatabaseConstructor::initialize)
                .addMigrations(EviloDatabase.MIGRATION_1_2)
                .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()
            createAppStorage(OkioStorage(FileSystem.SYSTEM, EviloPreferencesSerializer,
                producePath = { context.filesDir.resolve("evilo.preferences.pb").absolutePath.toPath() }), database, scope,
                access = androidStorageAccess)
        }
    }
}
