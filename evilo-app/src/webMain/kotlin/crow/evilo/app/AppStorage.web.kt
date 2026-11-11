package crow.evilo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.core.okio.WebOpfsStorage
import crow.evilo.storage.EviloDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.w3c.dom.ErrorEvent
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker
import org.w3c.dom.events.Event

internal expect fun createSqliteWorker(): Worker
internal expect fun createBrowserDatabase(worker: Worker): EviloDatabase
internal expect fun supportsBrowserPersistence(): Boolean
internal expect fun requestSqliteWorkerReady(worker: Worker)
internal expect fun requestSqliteWorkerClose(worker: Worker)
internal expect fun sqliteWorkerReady(event: MessageEvent): Boolean
internal expect fun sqliteWorkerClosed(event: MessageEvent): Boolean
internal expect fun sqliteWorkerError(event: MessageEvent): String?

@Composable
internal actual fun rememberAppStorageFactory(): AppStorageFactory = remember {
    AppStorageFactory { scope ->
        check(supportsBrowserPersistence()) { "Web persistence requires HTTPS (or localhost), Web Workers, OPFS, and Web Locks." }
        val worker = createSqliteWorker()
        try {
            awaitSqliteWorker(worker)
            createAppStorage(WebOpfsStorage(EviloPreferencesSerializer, "evilo.preferences.pb"), createBrowserDatabase(worker), scope) {
                closeSqliteWorker(worker)
            }
        } catch (failure: Throwable) {
            worker.terminate()
            throw failure
        }
    }
}

private suspend fun awaitSqliteWorker(worker: Worker) = awaitWorkerReply(worker, 30_000, ::sqliteWorkerReady) {
    requestSqliteWorkerReady(worker)
}

private suspend fun closeSqliteWorker(worker: Worker) {
    try {
        awaitWorkerReply(worker, 10_000, ::sqliteWorkerClosed) { requestSqliteWorkerClose(worker) }
    } finally {
        worker.terminate()
    }
}

private suspend fun awaitWorkerReply(worker: Worker, timeoutMs: Long, completed: (MessageEvent) -> Boolean, request: () -> Unit) {
    val ready = CompletableDeferred<Unit>()
    val onMessage: (Event) -> Unit = { event ->
        val message = event as? MessageEvent
        if (message != null) {
            val error = sqliteWorkerError(message)
            if (error != null) ready.completeExceptionally(IllegalStateException(error))
            else if (completed(message)) ready.complete(Unit)
        }
    }
    val onError: (Event) -> Unit = { event ->
        ready.completeExceptionally(IllegalStateException((event as? ErrorEvent)?.message ?: "SQLite Worker could not start."))
    }
    worker.addEventListener("message", onMessage)
    worker.addEventListener("error", onError)
    try {
        request()
        withTimeout(timeoutMs) { ready.await() }
    } finally {
        worker.removeEventListener("message", onMessage)
        worker.removeEventListener("error", onError)
    }
}
