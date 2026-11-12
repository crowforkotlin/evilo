@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package crow.evilo.architecture

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LatestOperationTest {
    @Test
    fun explicitCancellationSuppressesLateErrorsAndStillWaitsForCleanup() = runTest {
        val operations = LatestOperation(this)
        val gate = CompletableDeferred<Unit>()
        var failures = 0
        var finishes = 0
        var cleaned = false
        try {
            operations.launch(onFailure = { failures++ }, onFinish = { finishes++ }) {
                try {
                    withContext(NonCancellable) { gate.await() }
                    error("Obsolete failure.")
                } finally { cleaned = true }
            }
            runCurrent()
            operations.cancel()
            gate.complete(Unit)
            operations.awaitIdle()
            assertTrue(cleaned)
            assertEquals(0, failures)
            assertEquals(0, finishes)
            operations.launch(onFailure = { throw it }, onFinish = { finishes++ }) {}
            operations.awaitIdle()
            assertEquals(1, finishes)
        } finally { gate.complete(Unit); operations.close() }
    }

    @Test
    fun awaitingIdleFollowsAReplacementAndIgnoresObsoleteFinalization() = runTest {
        val operations = LatestOperation(this)
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        var finishes = 0
        var idle = false
        try {
            operations.launch(onFailure = { throw it }, onFinish = { finishes++ }) { firstGate.await() }
            runCurrent()
            val waiter = launch { operations.awaitIdle(); idle = true }
            runCurrent()
            operations.launch(onFailure = { throw it }, onFinish = { finishes++ }) { secondGate.await() }
            runCurrent()
            assertFalse(idle)
            assertEquals(0, finishes)
            secondGate.complete(Unit)
            waiter.join()
            assertTrue(idle)
            assertEquals(1, finishes)
        } finally {
            firstGate.complete(Unit)
            secondGate.complete(Unit)
            operations.close()
        }
    }
}
