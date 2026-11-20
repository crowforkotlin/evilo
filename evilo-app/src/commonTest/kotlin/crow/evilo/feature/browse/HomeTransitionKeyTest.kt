package crow.evilo.feature.browse

import crow.evilo.ui.EviloTransitionPayloadCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class HomeTransitionKeyTest {
    @Test
    fun rootTabsKeepTheShellButHaveSeparateContentTargets() {
        val browse = PluginHomeState(destination = HomeDestination.BROWSE)
        val more = browse.copy(destination = HomeDestination.MORE)
        val shell = EviloTransitionPayloadCache(browse, ::homeTransitionKey)
        val contents = EviloTransitionPayloadCache(browse) { it.destination }
        val originalShell = shell.target(browse)
        val outgoingContent = contents.target(browse)

        assertEquals(homeTransitionKey(browse), homeTransitionKey(more))
        assertSame(originalShell, shell.target(more))
        assertNotSame(outgoingContent, contents.target(more))
        assertEquals(HomeDestination.BROWSE, outgoingContent.value.destination)
        assertEquals(HomeDestination.MORE, originalShell.value.destination)
    }

    @Test
    fun enteringAPageChangesTheShellTarget() {
        val root = PluginHomeState()
        val page = root.copy(pages = listOf(HomePage.Sources))
        assertNotEquals(homeTransitionKey(root), homeTransitionKey(page))
        val shell = EviloTransitionPayloadCache(root, ::homeTransitionKey)
        assertNotSame(shell.target(root), shell.target(page))
    }
}
