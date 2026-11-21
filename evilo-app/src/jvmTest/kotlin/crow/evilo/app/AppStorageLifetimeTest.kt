package crow.evilo.app

import androidx.datastore.core.okio.OkioStorage
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import crow.evilo.storage.EviloDatabase
import crow.evilo.storage.EviloDatabaseConstructor
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppStorageLifetimeTest {
    @Test
    fun replacementWaitsUntilThePreviousDataStoreHasClosed() = runTest {
        val directory = Files.createTempDirectory("evilo-storage-lifetime-").toString().toPath()
        val access = Mutex()
        suspend fun open(): AppStorage {
            val database = Room.databaseBuilder<EviloDatabase>((directory / "catalogs.db").toString(),
                EviloDatabaseConstructor::initialize)
                .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()
            return createAppStorage(OkioStorage(FileSystem.SYSTEM, EviloPreferencesSerializer,
                producePath = { directory / "preferences.pb" }), database, backgroundScope, access = access)
        }
        var first: AppStorage? = null
        var replacement: AppStorage? = null
        try {
            first = open()
            val expected = first.preferences.data.first()
            val pending = async { open().also { replacement = it } }
            runCurrent()
            assertFalse(pending.isCompleted)
            first.close()
            assertEquals(expected, pending.await().preferences.data.first())
        } finally {
            first?.close()
            replacement?.close()
            FileSystem.SYSTEM.deleteRecursively(directory)
        }
    }

    @Test
    fun cancellationWhileWaitingDoesNotReleaseAnotherOwnersLease() = runTest {
        val directory = Files.createTempDirectory("evilo-storage-cancellation-").toString().toPath()
        val access = Mutex()
        suspend fun open(): AppStorage {
            val database = Room.databaseBuilder<EviloDatabase>((directory / "catalogs.db").toString(),
                EviloDatabaseConstructor::initialize)
                .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()
            return createAppStorage(OkioStorage(FileSystem.SYSTEM, EviloPreferencesSerializer,
                producePath = { directory / "preferences.pb" }), database, backgroundScope, access = access)
        }
        val first = open()
        var replacement: AppStorage? = null
        try {
            val cancelled = async { open() }
            runCurrent()
            cancelled.cancelAndJoin()
            val pending = async { open().also { replacement = it } }
            runCurrent()
            assertFalse(pending.isCompleted)
            first.close()
            pending.await().preferences.data.first()
        } finally {
            first.close()
            replacement?.close()
            FileSystem.SYSTEM.deleteRecursively(directory)
        }
    }
}
