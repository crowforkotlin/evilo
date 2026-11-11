@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package crow.evilo.app

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import crow.evilo.storage.EviloDatabase
import crow.evilo.storage.EviloDatabaseConstructor
import kotlin.js.js
import kotlinx.coroutines.Dispatchers
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker

internal actual fun createSqliteWorker(): Worker =
    js("new Worker(new URL('sqlite-worker/worker.js', document.baseURI), {type: 'module', name: 'evilo-sqlite'})")

internal actual fun createBrowserDatabase(worker: Worker): EviloDatabase =
    Room.databaseBuilder<EviloDatabase>("evilo.catalogs.db", EviloDatabaseConstructor::initialize)
        .setDriver(WebWorkerSQLiteDriver(worker)).setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .setQueryCoroutineContext(Dispatchers.Default).build()

internal actual fun supportsBrowserPersistence(): Boolean =
    js("!!(self.isSecureContext && typeof Worker !== 'undefined' && navigator.storage?.getDirectory && navigator.locks)")

internal actual fun sqliteWorkerReady(event: MessageEvent): Boolean = js("event.data?.eviloReady === true")
internal actual fun sqliteWorkerClosed(event: MessageEvent): Boolean = js("event.data?.eviloClosed === true")
internal actual fun sqliteWorkerError(event: MessageEvent): String? = js("typeof event.data?.error === 'string' ? event.data.error : null")
internal actual fun requestSqliteWorkerReady(worker: Worker): Unit = js("worker.postMessage({eviloInitialize: true})")
internal actual fun requestSqliteWorkerClose(worker: Worker): Unit = js("worker.postMessage({eviloShutdown: true})")
