package crow.evilo.feature.browse

import crow.evilo.model.MediaKind
import crow.evilo.plugin.catalog.PluginBlockCode
import crow.evilo.plugin.catalog.PluginRepositoryState
import crow.evilo.plugin.catalog.installationOrderTime

enum class BrowseEmptyReason { NONE, NO_INSTALLATIONS, SOURCES_DISABLED, UNSUPPORTED_KIND, NSFW_HIDDEN, SOURCE_FILTER, NO_MATCHES }

data class BrowseSearchLabels(
    val overseas: String = "Overseas",
    val domestic: String = "Domestic",
    val nsfw: String = "NSFW",
    val updateAvailable: String = "Update available",
    val sourceChoice: String = "Choose an installed source",
    val pendingSource: String = "Source selection required",
    val blocks: Map<PluginBlockCode, String> = emptyMap(),
)

data class BrowsePluginItem(
    val key: String,
    val entry: PluginEntry,
    val installationKey: String?,
    val sourceId: String?,
    val installedKeys: List<String>,
    val nsfw: Boolean,
    val releaseConflict: Boolean,
) {
    val pluginId get() = checkNotNull(entry.installation).plugin.id
    val needsSourceChoice get() = installationKey == null
    val updateAvailable get() = entry.installation?.let { installed ->
        entry.recommended?.versionCode?.let { it > installed.release.versionCode }
    } == true
}

data class BrowseSourceGroup(val sourceId: String?, val name: String?, val items: List<BrowsePluginItem>)

data class BrowseProjection(
    val groups: List<BrowseSourceGroup> = emptyList(),
    val totalBeforeSearch: Int = 0,
    val hiddenNsfw: Int = 0,
    val emptyReason: BrowseEmptyReason = BrowseEmptyReason.NO_INSTALLATIONS,
) {
    val items get() = groups.flatMap { it.items }
    val count get() = groups.sumOf { it.items.size }
}

/** Display-only grouping: every action retains its source-qualified installation key. */
fun projectBrowse(
    catalog: PluginRepositoryState,
    entries: Map<String, PluginEntry>,
    kind: MediaKind = catalog.selectedKind,
    query: String = "",
    sourceId: String? = null,
    searchLabels: BrowseSearchLabels = BrowseSearchLabels(),
): BrowseProjection {
    val installed = entries.values.filter { it.installation != null }
    if (installed.isEmpty()) return BrowseProjection()
    val byPlugin = installed.groupBy { checkNotNull(it.installation).plugin.id }
    val sources = catalog.subscriptions.associateBy { it.id }
    val enabled = installed.filter { sources[it.installation?.sourceId]?.enabled == true }
    if (enabled.isEmpty()) return BrowseProjection(emptyReason = BrowseEmptyReason.SOURCES_DISABLED)
    val supported = enabled.filter { kind in checkNotNull(it.installation).release.kinds }
    if (supported.isEmpty()) return BrowseProjection(emptyReason = BrowseEmptyReason.UNSUPPORTED_KIND)
    val candidates = supported.filter { sourceId == null || it.installation?.sourceId == sourceId }
    if (candidates.isEmpty()) return BrowseProjection(emptyReason = BrowseEmptyReason.SOURCE_FILTER)
    val hidden = candidates.filter { !catalog.showNsfw && catalog.isPluginNsfw(checkNotNull(it.installation).plugin.id) }
    val hiddenCount = if (catalog.mergeDuplicatePlugins) hidden.distinctBy { it.installation?.plugin?.id }.size else hidden.size
    val visible = candidates - hidden.toSet()
    if (visible.isEmpty()) return BrowseProjection(hiddenNsfw = hiddenCount, emptyReason = BrowseEmptyReason.NSFW_HIDDEN)
    val rows = if (catalog.mergeDuplicatePlugins && sourceId == null) {
        visible.groupBy { checkNotNull(it.installation).plugin.id }.map { (id, choices) ->
            val all = byPlugin.getValue(id)
            val preferred = preferredInstallationKey(catalog, id) ?: oldestInstallation(all, catalog).key
            val primary = choices.firstOrNull { it.key == preferred }
            browseItem(primary ?: oldestInstallation(choices, catalog), all, catalog,
                key = "plugin:$id", installationKey = primary?.key, sourceId = primary?.installation?.sourceId)
        }
    } else visible.map { entry ->
        browseItem(entry, byPlugin.getValue(checkNotNull(entry.installation).plugin.id), catalog,
            entry.key, entry.key, checkNotNull(entry.installation).sourceId)
    }
    val search = query.trim()
    val filtered = rows.filter { row ->
        val plugin = row.entry.plugin
        val sourceName = row.sourceId?.let { id -> catalog.sourceDisplayName(id) } ?: searchLabels.pendingSource
        val labels = buildList {
            add(if (plugin.overseas) searchLabels.overseas else searchLabels.domestic)
            if (row.nsfw) add(searchLabels.nsfw)
            if (row.updateAvailable) add(searchLabels.updateAvailable)
            if (row.needsSourceChoice) add(searchLabels.sourceChoice)
            row.entry.openBlock?.let { searchLabels.blocks[it.code]?.let(::add) }
            addAll(checkNotNull(row.entry.installation).release.languages)
        }
        search.isEmpty() || (plugin.name + " " + sourceName + " " + labels.joinToString(" ")).contains(search, ignoreCase = true)
    }
    val order = catalog.subscriptions.mapIndexed { index, source -> source.id to index }.toMap()
    val groups = filtered.groupBy { it.sourceId }.map { (id, items) ->
        BrowseSourceGroup(id, id?.let(catalog::sourceDisplayName),
            items.sortedWith(compareBy<BrowsePluginItem> { it.entry.plugin.name.lowercase() }.thenBy { it.key }))
    }.sortedBy { order[it.sourceId] ?: Int.MAX_VALUE }
    return BrowseProjection(groups, rows.size, hiddenCount,
        if (filtered.isEmpty()) BrowseEmptyReason.NO_MATCHES else BrowseEmptyReason.NONE)
}

internal fun oldestInstallation(entries: List<PluginEntry>, catalog: PluginRepositoryState): PluginEntry {
    val sourceOrder = catalog.subscriptions.mapIndexed { index, source -> source.id to index }.toMap()
    return entries.minWith(compareBy<PluginEntry> { installationOrderTime(checkNotNull(it.installation).installedAt) }
        .thenBy { sourceOrder[it.installation?.sourceId] ?: Int.MAX_VALUE }.thenBy { it.key })
}

fun preferredInstallationKey(catalog: PluginRepositoryState, pluginId: String): String? {
    catalog.preferredInstallations[pluginId]?.let { return it }
    val order = catalog.subscriptions.mapIndexed { index, source -> source.id to index }.toMap()
    return catalog.installed.filter { it.plugin.id == pluginId }.minWithOrNull(
        compareBy<crow.evilo.plugin.catalog.InstalledPlugin> { installationOrderTime(it.installedAt) }
            .thenBy { order[it.sourceId] ?: Int.MAX_VALUE }.thenBy { it.key })?.key
}

private fun browseItem(entry: PluginEntry, all: List<PluginEntry>, catalog: PluginRepositoryState,
    key: String, installationKey: String?, sourceId: String?): BrowsePluginItem = BrowsePluginItem(
    key, entry, installationKey, sourceId, all.map { it.key }, catalog.isPluginNsfw(checkNotNull(entry.installation).plugin.id),
    hasReleaseConflict(all),
)

fun hasReleaseConflict(entries: List<PluginEntry>): Boolean {
    val releases = entries.flatMap { entry ->
        (listOfNotNull(entry.installation?.release) + entry.listing?.plugin?.availableVersions.orEmpty()).map { entry.sourceId to it }
    }
    return releases.groupBy { it.second.versionCode }.values.any { versions ->
        versions.any { (source, release) -> versions.any { (otherSource, otherRelease) ->
            source != otherSource && listOf(release.version, release.kinds, release.languages, release.protocol, release.permissions,
                release.sourceRevision, release.sourceArchiveSha256) != listOf(otherRelease.version, otherRelease.kinds,
                otherRelease.languages, otherRelease.protocol, otherRelease.permissions, otherRelease.sourceRevision, otherRelease.sourceArchiveSha256)
        } }
    }
}

data class SourceCatalogProjection(val entries: List<PluginEntry>, val hiddenNsfw: Int)

fun projectSourceCatalog(state: PluginHomeState, sourceId: String): SourceCatalogProjection {
    val filter = state.sourceFilters[sourceId] ?: CatalogFilter.ALL
    val entries = state.entries.values.filter { entry -> entry.sourceId == sourceId && when (filter) {
        CatalogFilter.INSTALLED -> entry.installation?.release?.kinds?.contains(state.catalog.selectedKind) == true
        CatalogFilter.ALL -> state.catalog.selectedKind in entry.plugin.kinds ||
            entry.installation?.release?.kinds?.contains(state.catalog.selectedKind) == true
    } }
    val hidden = entries.filter { !state.catalog.showNsfw && (it.plugin.nsfw || state.catalog.isPluginNsfw(it.plugin.id)) }
    val query = state.sourceQueries[sourceId].orEmpty().trim()
    return SourceCatalogProjection((entries - hidden.toSet()).filter { entry ->
        (filter != CatalogFilter.INSTALLED || entry.installation != null) &&
            (query.isEmpty() || (entry.plugin.name + " " + entry.plugin.description + " " +
                entry.plugin.authors.joinToString { it.name }).contains(query, ignoreCase = true))
    }.sortedWith(compareBy<PluginEntry> { it.plugin.name.lowercase() }.thenBy { it.key }), hidden.size)
}

data class InstalledSourceGroup(val sourceId: String, val entries: List<PluginEntry>)
data class InstalledProjection(val groups: List<InstalledSourceGroup>, val hiddenNsfw: Int)

fun projectInstalledManagement(state: PluginHomeState): InstalledProjection {
    val installed = state.entries.values.filter { it.installation != null }
    val hidden = installed.filter { !state.catalog.showNsfw && state.catalog.isPluginNsfw(it.plugin.id) }
    val query = state.installedQuery.trim()
    val visible = (installed - hidden.toSet()).filter { entry ->
        val source = state.catalog.sourceDisplayName(entry.sourceId)
        query.isEmpty() || (entry.plugin.name + " " + source).contains(query, ignoreCase = true)
    }
    val order = state.catalog.subscriptions.mapIndexed { index, source -> source.id to index }.toMap()
    val groups = visible.groupBy { it.sourceId }.map { (id, entries) ->
        InstalledSourceGroup(id, entries.sortedWith(compareBy<PluginEntry> { it.plugin.name.lowercase() }.thenBy { it.key }))
    }.sortedBy { order[it.sourceId] ?: Int.MAX_VALUE }
    return InstalledProjection(groups, hidden.size)
}

fun PluginRepositoryState.sourceDisplayName(sourceId: String): String =
    snapshots.firstOrNull { it.subscription.id == sourceId }?.catalog?.name
        ?: (subscriptions.firstOrNull { it.id == sourceId }?.url ?: installed.firstOrNull { it.sourceId == sourceId }?.sourceUrl)
            ?.substringAfter("://")?.substringBefore('/')?.substringBefore('?')
        ?: sourceId
