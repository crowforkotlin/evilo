package crow.evilo.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class EviloTransitionPayloadTest {
    @Test
    fun mapKeysNeverHashOrComparePageData() {
        val value = UnhashablePage("page")
        val first = EviloTransitionPayload(value, value.route)
        val second = EviloTransitionPayload(value, value.route)
        val contents = hashMapOf(first to "outgoing", second to "incoming")

        assertEquals(2, contents.size)
        assertEquals("outgoing", contents[first])
        assertEquals("incoming", contents[second])
    }

    @Test
    fun nonNullPreviousStateCanUseANullableTransitionPayload() {
        val previous: String? = "previous"
        val target: EviloTransitionPayload<String?>? = previous?.let { EviloTransitionPayload<String?>(it, it) }
        assertEquals("previous", target?.value)
    }

    @Test
    fun sameRouteUpdatesContentWithoutReplacingTheAnimationTarget() {
        val first = UnhashablePage("page")
        val second = UnhashablePage("page")
        val cache = EviloTransitionPayloadCache(first) { it.route }
        val target = cache.target(first)

        assertSame(target, cache.target(second))
        assertSame(second, target.value)
    }

    @Test
    fun newRouteRetainsTheOutgoingPageSnapshot() {
        val first = UnhashablePage("first")
        val second = UnhashablePage("second")
        val cache = EviloTransitionPayloadCache(first) { it.route }
        val outgoing = cache.target(first)
        val incoming = cache.target(second)

        assertNotSame(outgoing, incoming)
        assertSame(first, outgoing.value)
        assertSame(second, incoming.value)
    }

    @Test
    fun committedPreviewIsReusedWhenTheStorePublishesItsDestination() {
        val current = UnhashablePage("current")
        val previous = UnhashablePage("previous")
        val cache = EviloTransitionPayloadCache(current) { it.route }
        val preview = EviloTransitionPayload(previous, previous.route)
        cache.adopt(preview)
        val published = UnhashablePage("previous")

        assertSame(preview, cache.target(published))
        assertSame(published, preview.value)
    }

    @Test
    fun equalResourceTitlesDoNotCreateANewAnimationTarget() {
        val first = charArrayOf('T', 'i', 't', 'l', 'e').concatToString()
        val second = charArrayOf('T', 'i', 't', 'l', 'e').concatToString()
        val cache = EviloTransitionPayloadCache(first) { it }
        assertSame(cache.target(first), cache.target(second))
    }

    @Test
    fun predictiveBackAndPageAnimationShareTheCurrentTarget() {
        val current = UnhashablePage("page")
        val state = EviloBackTransitionState(current) { it.route }
        assertSame(state.transition.currentState, state.target(current))
    }

    private class UnhashablePage(val route: String) {
        override fun hashCode(): Int = error("Animation keys must not hash page data.")
        override fun equals(other: Any?): Boolean = error("Animation keys must not compare page data.")
    }
}
