package crow.evilo.architecture

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Attempts every disposal step and preserves subsequent failures as suppressed exceptions. */
suspend fun closeResources(vararg steps: suspend () -> Unit): Unit = withContext(NonCancellable) {
    var failure: Throwable? = null
    steps.forEach { close ->
        try {
            close()
        } catch (error: Throwable) {
            val previous = failure
            if (previous == null) failure = error else if (previous !== error) previous.addSuppressed(error)
        }
    }
    failure?.let { throw it }
}
