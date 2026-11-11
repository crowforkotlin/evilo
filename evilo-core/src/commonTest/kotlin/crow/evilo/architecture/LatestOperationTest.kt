@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package crow.evilo.architecture

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LatestOperationTest {
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
