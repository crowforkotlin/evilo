package crow.evilo.architecture

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** UI-dispatcher controller that cancels obsolete work and serializes replacement cleanup. */
class LatestOperation(parentScope: CoroutineScope) {
    private val lifetime = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + lifetime)
    private val operations = Mutex()
    private var operation: Job? = null
    private var generation = 0L
    private var closed = false

    fun launch(
        onStart: () -> Unit = {},
        onFailure: (Exception) -> Unit,
        onFinish: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        if (closed) return
        val token = ++generation
        operation?.cancel()
        onStart()
        operation = scope.launch {
            try {
                operations.withLock {
                    currentCoroutineContext().ensureActive()
                    block()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generation == token) onFailure(failure)
            } finally {
                if (generation == token) onFinish()
            }
        }
    }

    suspend fun awaitIdle() {
        while (true) {
            val current = operation ?: return
            current.join()
            if (operation === current) return
        }
    }

    suspend fun close() = withContext(NonCancellable) {
        if (!closed) {
            closed = true
            generation++
            lifetime.cancelAndJoin()
        }
    }
}
