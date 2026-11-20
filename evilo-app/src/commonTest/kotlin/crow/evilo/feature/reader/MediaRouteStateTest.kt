package crow.evilo.feature.reader

import crow.evilo.model.MediaKind
import crow.evilo.ui.EviloTransitionPayloadCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MediaRouteStateTest {
    @Test
    fun packagePreparationAndSessionArrivalKeepTheCatalogTarget() {
        val initial = MediaBrowserState(MediaKind.MANGA)
        val pending = mediaRouteState(initial, initial.kind, sessionReady = false, connecting = true, connectionError = null)
        val firstRequest = mediaRouteState(initial, initial.kind, sessionReady = true, connecting = false, connectionError = null)
        val results = mediaRouteState(initial.copy(searchPage = 1), initial.kind, sessionReady = true, connecting = false, connectionError = null)
        val payloads = EviloTransitionPayloadCache(pending, ::mediaPageKey)
        val target = payloads.target(pending)
        assertSame(target, payloads.target(firstRequest))
        assertSame(target, payloads.target(results))
        assertTrue(pending.busy)
        assertTrue(firstRequest.busy)
        assertFalse(results.busy)
    }

    @Test
    fun packageFailureStaysOnTheCatalogAndRetainsTheFailure() {
        val pending = mediaRouteState(MediaBrowserState(MediaKind.NOVEL), MediaKind.NOVEL,
            sessionReady = false, connecting = false, connectionError = "Signature verification failed.")
        assertEquals(MediaBrowserPage.CATALOG, pending.page)
        assertEquals("Signature verification failed.", pending.error)
        assertFalse(pending.busy)
    }

    @Test
    fun failedFirstRequestIsNotConvertedToAnInfiniteLoadingState() {
        val failed = MediaBrowserState(MediaKind.MANGA, error = "Request failed.")
        assertSame(failed, mediaRouteState(failed, failed.kind, sessionReady = true, connecting = false, connectionError = null))
    }

    @Test
    fun catalogOperationsSelectExactlyOneLoadingLocation() {
        assertEquals(CatalogLoadingPlacement.CENTER, catalogLoadingPlacement(busy = true, hasItems = false, refreshing = false))
        assertEquals(CatalogLoadingPlacement.FOOTER, catalogLoadingPlacement(busy = true, hasItems = true, refreshing = false))
        for (hasItems in listOf(false, true)) {
            assertEquals(CatalogLoadingPlacement.PULL, catalogLoadingPlacement(busy = true, hasItems = hasItems, refreshing = true))
            for (refreshing in listOf(false, true)) {
                assertEquals(CatalogLoadingPlacement.NONE, catalogLoadingPlacement(busy = false, hasItems = hasItems, refreshing = refreshing))
                // Failure publishes before onFinish clears busy; hide the loader immediately.
                assertEquals(CatalogLoadingPlacement.NONE, catalogLoadingPlacement(busy = true, hasItems = hasItems,
                    refreshing = refreshing, error = "Request failed."))
            }
        }
    }

    @Test
    fun sharedCoverMatchesOnlyTheSameScopeMediaAndArtwork() {
        val scope = Any()
        val cover = MediaCoverKey(scope, "plugin:manga:123", "https://images.example/123.jpg")
        assertEquals(cover, MediaCoverKey(scope, cover.mediaId, cover.url))
        assertNotEquals(cover, cover.copy(scope = Any()))
        assertNotEquals(cover, cover.copy(mediaId = "plugin:manga:456"))
        assertNotEquals(cover, cover.copy(url = "https://images.example/123-new.jpg"))
    }

    @Test
    fun readyDetailsAndReaderStatesAreNotReplacedByCatalogLoading() {
        for (page in listOf(MediaBrowserPage.DETAILS, MediaBrowserPage.READER)) {
            val state = MediaBrowserState(MediaKind.MANGA, page = page)
            assertSame(state, mediaRouteState(state, state.kind, sessionReady = true, connecting = false, connectionError = null))
        }
    }
}
