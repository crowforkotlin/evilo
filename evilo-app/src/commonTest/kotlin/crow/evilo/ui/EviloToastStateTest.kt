package crow.evilo.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class EviloToastStateTest {
    @Test
    fun repeatedIdenticalMessagesReceiveANewId() {
        val state = EviloToastState()
        val first = state.show("Operation failed")
        val second = state.show("Operation failed")
        assertNotEquals(first, second)
        assertEquals(second, state.current?.id)
    }

    @Test
    fun dismissalOfAnOlderMessageCannotCloseItsReplacement() {
        val state = EviloToastState()
        val first = state.show("First failure")
        state.show("Second failure")
        val replacement = state.current
        state.dismiss(first)
        assertSame(replacement, state.current)
    }

    @Test
    fun replacementCallsThePreviousDismissCallbackOnlyOnce() {
        val state = EviloToastState()
        var dismissals = 0
        val first = state.show("First failure", onDismiss = { dismissals++ })
        val second = state.show("Second failure")
        state.dismiss(first)
        assertEquals(1, dismissals)
        assertEquals(second, state.current?.id)
    }

    @Test
    fun actionsRemoveTheToastBeforeCallingItsCallbacksAndCannotRunTwice() {
        val state = EviloToastState()
        val calls = mutableListOf<String>()
        val id = state.show("Installed", actionLabel = "Browse", onAction = {
            assertNull(state.current)
            calls += "action"
        }, onDismiss = { calls += "dismiss" })
        state.performAction(id)
        state.performAction(id)
        assertNull(state.current)
        assertEquals(listOf("action", "dismiss"), calls)
    }

    @Test
    fun sourceStateChangesCanDismissSilently() {
        val state = EviloToastState()
        var dismissals = 0
        val id = state.show("Failure", onDismiss = { dismissals++ })
        state.dismiss(id, notify = false)
        assertNull(state.current)
        assertEquals(0, dismissals)
    }
}
