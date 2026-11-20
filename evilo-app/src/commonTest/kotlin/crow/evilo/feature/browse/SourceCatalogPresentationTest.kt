package crow.evilo.feature.browse

import crow.evilo.plugin.catalog.CatalogSnapshot
import crow.evilo.plugin.catalog.CatalogSubscription
import crow.evilo.plugin.catalog.PluginCatalog
import crow.evilo.plugin.catalog.SourceCheckState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceCatalogPresentationTest {
    @Test
    fun cacheWithoutAnErrorDoesNotCreateAFailure() {
        val check = SourceCheckState(usingCache = true)
        val snapshot = snapshot(fromCache = true)
        assertNull(sourceCheckError(check, snapshot))
        assertEquals(SourceCatalogPresentation.EMPTY, sourceCatalogPresentation(false, check, snapshot, refreshing = false))
    }

    @Test
    fun failedRequestWithoutACatalogIsNotAnEmptySuccess() {
        val check = SourceCheckState(error = "HTTP 503")
        assertEquals("HTTP 503", sourceCheckError(check, null))
        assertEquals(SourceCatalogPresentation.FAILURE, sourceCatalogPresentation(false, check, null, refreshing = false))
    }

    @Test
    fun failedRefreshKeepsEntriesAndTheActualError() {
        val check = SourceCheckState(error = "Invalid catalog field", usingCache = true)
        val snapshot = snapshot(error = "Invalid catalog field", fromCache = true)
        assertEquals(SourceCatalogPresentation.CONTENT, sourceCatalogPresentation(true, check, snapshot, refreshing = false))
        assertEquals("Invalid catalog field", sourceCheckError(check, snapshot))
        assertEquals(SourceCatalogPresentation.FAILURE, sourceCatalogPresentation(false, check, snapshot, refreshing = false))
    }

    @Test
    fun retryKeepsPreviousSnapshotFailureUntilASuccessReplacesIt() {
        val check = SourceCheckState(running = true)
        val snapshot = snapshot(error = "Connection refused", fromCache = true)
        assertEquals("Connection refused", sourceCheckError(check, snapshot))
        assertEquals(SourceCatalogPresentation.FAILURE, sourceCatalogPresentation(false, check, snapshot, refreshing = true))
    }

    @Test
    fun currentCheckFailureTakesPrecedenceOverAnOlderSnapshotFailure() {
        val check = SourceCheckState(error = "HTTP 502")
        assertEquals("HTTP 502", sourceCheckError(check, snapshot(error = "HTTP 500")))
    }

    @Test
    fun initialRequestShowsLoadingWithoutReplacingExistingEntries() {
        val check = SourceCheckState(running = true)
        assertEquals(SourceCatalogPresentation.LOADING, sourceCatalogPresentation(false, check, null, refreshing = true))
        assertEquals(SourceCatalogPresentation.CONTENT, sourceCatalogPresentation(true, check, snapshot(), refreshing = true))
    }

    @Test
    fun queuedInitialRefreshIsNotAnEmptyCatalog() {
        assertEquals(SourceCatalogPresentation.LOADING, sourceCatalogPresentation(false, null, null, refreshing = true))
        assertEquals(SourceCatalogPresentation.EMPTY, sourceCatalogPresentation(false, null, snapshot(), refreshing = true))
    }

    @Test
    fun requestFailureWinsOverTheBriefErrorBeforeBusyClearsState() {
        val check = SourceCheckState(running = true, error = "Timed out")
        assertEquals(SourceCatalogPresentation.FAILURE, sourceCatalogPresentation(false, check, null, refreshing = true))
    }

    private fun snapshot(error: String? = null, fromCache: Boolean = false) = CatalogSnapshot(
        subscription = CatalogSubscription("source", "https://example.com/plugins.json"),
        catalog = PluginCatalog(1, "source", "Source", "", "2026-01-01", emptyList()),
        error = error,
        fromCache = fromCache,
    )
}
