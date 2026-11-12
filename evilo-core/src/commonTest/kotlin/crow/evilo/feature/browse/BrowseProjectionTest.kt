package crow.evilo.feature.browse

import crow.evilo.model.MediaKind
import crow.evilo.plugin.api.HttpAccess
import crow.evilo.plugin.api.PluginPermissions
import crow.evilo.plugin.catalog.CatalogListing
import crow.evilo.plugin.catalog.CatalogPlugin
import crow.evilo.plugin.catalog.CatalogSnapshot
import crow.evilo.plugin.catalog.CatalogSubscription
import crow.evilo.plugin.catalog.InstalledPlugin
import crow.evilo.plugin.catalog.PluginBlock
import crow.evilo.plugin.catalog.PluginBlockCode
import crow.evilo.plugin.catalog.PluginRelease
import crow.evilo.plugin.catalog.PluginRepositoryState
import crow.evilo.plugin.catalog.catalogPlugin
import crow.evilo.plugin.catalog.catalogRelease
import crow.evilo.plugin.catalog.pluginCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowseProjectionTest {
    @Test
    fun addingCatalogEntriesDoesNotCreateInstalledBrowseRows() {
        val entry = entry("a", installed = false)
        val state = catalog(listOf(entry))
        assertEquals(BrowseEmptyReason.NO_INSTALLATIONS, projectBrowse(state, mapOf(entry.key to entry)).emptyReason)
        assertEquals(1, projectSourceCatalog(PluginHomeState(catalog = state, entries = mapOf(entry.key to entry)), "a").entries.size)
    }

    @Test
    fun installedReleaseKindsDoNotInheritNewCatalogCapabilities() {
        val manga = catalogRelease()
        val novel = catalogRelease(2).copy(kinds = setOf(MediaKind.NOVEL))
        val entry = entry("a", catalogPlugin(listOf(manga, novel)).copy(kinds = setOf(MediaKind.MANGA, MediaKind.NOVEL)), manga)
        val state = catalog(listOf(entry))
        val entries = mapOf(entry.key to entry)
        assertEquals(1, projectBrowse(state, entries, MediaKind.MANGA).count)
        assertEquals(BrowseEmptyReason.UNSUPPORTED_KIND, projectBrowse(state, entries, MediaKind.NOVEL).emptyReason)
        val home = PluginHomeState(catalog = state.copy(selectedKind = MediaKind.NOVEL), entries = entries,
            sourceFilters = mapOf("a" to CatalogFilter.INSTALLED))
        assertTrue(projectSourceCatalog(home, "a").entries.isEmpty())
        assertEquals(1, projectSourceCatalog(home.copy(sourceFilters = emptyMap()), "a").entries.size)
    }

    @Test
    fun defaultSourceUsesChronologicalInstallationTimeNotMirrorVersionOrStringOrdering() {
        val later = entry("b", catalogPlugin(listOf(catalogRelease(2))), installedAt = "2026-11-11T00:00:00.1Z")
        val earlier = entry("a", installedAt = "2026-11-11T00:00:00Z")
        val state = catalog(listOf(later, earlier))
        val projected = projectBrowse(state, listOf(later, earlier).associateBy { it.key })
        assertEquals(earlier.key, projected.items.single().installationKey)
        assertEquals(listOf(later.key, earlier.key), projected.items.single().installedKeys)
        assertEquals(2, state.installed.size)
    }

    @Test
    fun mergingUsesInstalledIdsNotNamesOrUnverifiedCatalogClaims() {
        val a = entry("a")
        val b = entry("b")
        val unverified = entry("c", catalogPlugin().copy(name = "Unverified catalog name"), installed = false)
        val other = entry("a", catalogPlugin().copy(id = "other.plugin"))
        val all = listOf(a, b, unverified, other)
        val state = catalog(all)
        val entries = all.associateBy { it.key }
        val merged = projectBrowse(state, entries)
        assertEquals(2, merged.count)
        assertEquals(setOf("test.plugin", "other.plugin"), merged.items.map { it.pluginId }.toSet())
        assertEquals(3, projectBrowse(state.copy(mergeDuplicatePlugins = false), entries).count)
        assertEquals(3, state.installed.size)
        assertFalse(merged.items.any { it.entry.plugin.name == unverified.plugin.name })
    }

    @Test
    fun searchDoesNotChooseAnotherSourceAndExplicitSourceFilteringDoesNotChangeThePreference() {
        val a = entry("a", sourceName = "Source Alpha")
        val b = entry("b", sourceName = "Source Beta")
        val entries = listOf(a, b).associateBy { it.key }
        val state = catalog(listOf(a, b)).copy(preferredInstallations = mapOf(a.plugin.id to a.key))
        assertEquals(BrowseEmptyReason.NO_MATCHES, projectBrowse(state, entries, query = "Beta").emptyReason)
        assertEquals(b.key, projectBrowse(state, entries, query = "Beta", sourceId = "b").items.single().installationKey)
        assertEquals(a.key, state.preferredInstallations[a.plugin.id])
    }

    @Test
    fun disabledOrRemovedPreferredSourceRequiresExplicitSelection() {
        val a = entry("a")
        val b = entry("b")
        val entries = listOf(a, b).associateBy { it.key }
        val initial = catalog(listOf(a, b)).copy(preferredInstallations = mapOf(a.plugin.id to a.key))
        val disabled = initial.copy(subscriptions = initial.subscriptions.map { it.copy(enabled = it.id != "a") })
        val row = projectBrowse(disabled, entries).items.single()
        assertTrue(row.needsSourceChoice)
        assertNull(row.sourceId)
        assertEquals(b.key, projectBrowse(disabled, entries, sourceId = "b").items.single().installationKey)
        val removed = disabled.copy(subscriptions = disabled.subscriptions.filterNot { it.id == "a" }, installed = listOf(b.installation!!))
        assertTrue(projectBrowse(removed, mapOf(b.key to b)).items.single().needsSourceChoice)
    }

    @Test
    fun unsupportedPreferredKindRequiresSelectionButMandatoryUpdateDoesNotAutomaticallySwitch() {
        val a = entry("a", block = PluginBlock(PluginBlockCode.MANDATORY_UPDATE, "Update required."))
        val novel = catalogRelease().copy(kinds = setOf(MediaKind.NOVEL))
        val b = entry("b", catalogPlugin(listOf(novel)).copy(kinds = novel.kinds))
        val entries = listOf(a, b).associateBy { it.key }
        val state = catalog(listOf(a, b)).copy(preferredInstallations = mapOf(a.plugin.id to a.key))
        assertTrue(projectBrowse(state, entries, MediaKind.NOVEL).items.single().needsSourceChoice)
        val manga = projectBrowse(state, entries, MediaKind.MANGA).items.single()
        assertEquals(a.key, manga.installationKey)
        assertEquals(PluginBlockCode.MANDATORY_UPDATE, manga.entry.openBlock?.code)
    }

    @Test
    fun installedNsfwClassificationIsConservativeAcrossSourcesButUninstalledClaimsDoNotOverrideIt() {
        val a = entry("a")
        val b = entry("b", catalogPlugin().copy(nsfw = true))
        val entries = listOf(a, b).associateBy { it.key }
        val state = catalog(listOf(a, b))
        val hidden = projectBrowse(state, entries)
        assertEquals(BrowseEmptyReason.NSFW_HIDDEN, hidden.emptyReason)
        assertEquals(1, hidden.hiddenNsfw)
        assertTrue(projectBrowse(state.copy(showNsfw = true), entries).items.single().nsfw)
        val uninstalled = b.copy(installation = null)
        assertEquals(1, projectBrowse(catalog(listOf(a, uninstalled)), mapOf(a.key to a, uninstalled.key to uninstalled)).count)
    }

    @Test
    fun conflictsCompareCrossSourceDeclarationsNotMirrorUrlsOrSingleSourceMetadataChanges() {
        val release = catalogRelease()
        val a = entry("a")
        val mirror = entry("b", catalogPlugin(listOf(release.copy(manifestUrl = "https://mirror.example.com/manifest.wlm",
            sourceArchiveUrl = "https://mirror.example.com/sources.zip"))))
        assertFalse(hasReleaseConflict(listOf(a, mirror)))
        val changed = release.copy(permissions = PluginPermissions(media = HttpAccess(origins = setOf("https://media.example.com"))))
        val conflict = entry("b", catalogPlugin(listOf(changed)))
        assertTrue(hasReleaseConflict(listOf(a, conflict)))
        assertFalse(hasReleaseConflict(listOf(a.copy(listing = conflict.listing?.copy(snapshot = a.listing!!.snapshot)))))
    }

    @Test
    fun localSearchMatchesLocalizedTagsAndReturnsActualFilteredGroupCounts() {
        val overseas = entry("a", catalogPlugin().copy(overseas = true))
        val domestic = entry("a", catalogPlugin().copy(id = "other.plugin", name = "Another plugin"))
        val entries = listOf(overseas, domestic).associateBy { it.key }
        val state = catalog(listOf(overseas, domestic))
        val result = projectBrowse(state, entries, query = "region-outside", searchLabels = BrowseSearchLabels(overseas = "region-outside"))
        assertEquals(1, result.count)
        assertEquals(1, result.groups.single().items.size)
        assertEquals(2, result.totalBeforeSearch)
        assertEquals(overseas.key, result.items.single().installationKey)
    }

    @Test
    fun managementRetainsDisabledOrOrphanInstallationsAndAllKindsWithoutDisplayMerging() {
        val a = entry("a")
        val b = entry("b").copy(listing = null)
        val initial = catalog(listOf(a, b))
        val state = initial.copy(subscriptions = initial.subscriptions.filter { it.id == "a" }.map { it.copy(enabled = false) }, selectedKind = MediaKind.NOVEL)
        val home = PluginHomeState(catalog = state, entries = listOf(a, b).associateBy { it.key })
        assertEquals(2, projectInstalledManagement(home).groups.sumOf { it.entries.size })
        assertEquals("b.example.com", state.sourceDisplayName("b"))
        assertEquals(BrowseEmptyReason.SOURCES_DISABLED, projectBrowse(state, home.entries).emptyReason)
    }

    private fun entry(source: String, plugin: CatalogPlugin = catalogPlugin(), release: PluginRelease = plugin.latestRelease,
        installed: Boolean = true, installedAt: String = "2026-11-11T00:00:00Z", sourceName: String = "Source $source", block: PluginBlock? = null): PluginEntry {
        val subscription = CatalogSubscription(source, "https://$source.example.com/catalog")
        val snapshot = CatalogSnapshot(subscription, pluginCatalog(listOf(plugin)).copy(name = sourceName))
        val installation = if (installed) InstalledPlugin(source, subscription.url, plugin, release, installedAt, false) else null
        return PluginEntry("$source:${plugin.id}", CatalogListing(snapshot, plugin), installation,
            plugin.availableVersions.map { PluginReleaseChoice(it, null) }, plugin.latestRelease, block?.message, block)
    }

    private fun catalog(entries: List<PluginEntry>): PluginRepositoryState {
        val groups = entries.groupBy { it.sourceId }
        val subscriptions = groups.map { (id, copies) -> copies.first().listing?.snapshot?.subscription
            ?: CatalogSubscription(id, copies.first().installation!!.sourceUrl) }
        val snapshots = groups.mapNotNull { (_, copies) -> copies.first().listing?.snapshot?.let { snapshot ->
            snapshot.copy(catalog = snapshot.catalog.copy(plugins = copies.mapNotNull { it.listing?.plugin }))
        } }
        return PluginRepositoryState(initialized = true, subscriptions = subscriptions, snapshots = snapshots, installed = entries.mapNotNull { it.installation })
    }
}
