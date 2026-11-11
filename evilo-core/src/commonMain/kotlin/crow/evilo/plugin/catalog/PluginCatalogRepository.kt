package crow.evilo.plugin.catalog

import crow.evilo.model.MediaKind
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.plugin.api.PluginDescriptor
import crow.evilo.plugin.api.PluginJson
import crow.evilo.plugin.api.incompatibility
import crow.evilo.network.ktor.BoundedHttpTransport
import crow.evilo.network.ktor.PluginUrlPolicy
import crow.evilo.storage.CatalogWrite
import crow.evilo.storage.EviloPreferencesRepository
import crow.evilo.storage.PluginCatalogStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

data class PluginRepositoryState(
    val initialized: Boolean = false,
    val refreshing: Boolean = false,
    val subscriptions: List<CatalogSubscription> = emptyList(),
    val snapshots: List<CatalogSnapshot> = emptyList(),
    val installed: List<InstalledPlugin> = emptyList(),
    val selectedKind: MediaKind = MediaKind.MANGA,
    val showNsfw: Boolean = false,
    val error: String? = null,
) {
    val catalogPluginByKey: Map<String, CatalogPlugin> = snapshots.flatMap { snapshot ->
        snapshot.catalog.plugins.map { "${snapshot.subscription.id}:${it.id}" to it }
    }.toMap()
    val listings: List<CatalogListing> = snapshots.filter { it.subscription.enabled }.flatMap { snapshot ->
        snapshot.catalog.plugins.map { CatalogListing(snapshot, it) }
    }
    val listingByKey: Map<String, CatalogListing> = listings.associateBy { it.key }
}

class PluginCatalogRepository(
    private val records: PluginCatalogStore,
    private val preferences: EviloPreferencesRepository,
    private val transport: BoundedHttpTransport,
    val host: HostDescriptor,
    private val officialUrl: String = DEFAULT_OFFICIAL_CATALOG_URL,
) {
    private val parser = CatalogParser()
    private val operations = Mutex()
    private val mutableState = MutableStateFlow(PluginRepositoryState())
    val state = mutableState.asStateFlow()
    private var updateFloors = emptyMap<String, Long>()

    fun dismissError() { mutableState.update { it.copy(error = null) } }

    suspend fun initialize(bundledOfficialJson: String) = operations.withLock {
        if (mutableState.value.initialized) return@withLock
        PluginUrlPolicy.check(officialUrl)
        val official = CatalogSubscription(OFFICIAL_SOURCE_ID, officialUrl)
        val stored = records.read()
        cleanupPluginData(stored.pendingDataDeletes)
        val saved = stored.subscriptions
        require(saved.size <= 16 && saved.distinctBy { it.id }.size == saved.size &&
            saved.distinctBy { it.url }.size == saved.size) { "Invalid stored plugin subscriptions." }
        saved.forEach {
            PluginUrlPolicy.check(it.url, it.allowHttp)
            require(it.id != OFFICIAL_SOURCE_ID || (it.url == officialUrl && !it.allowHttp)) {
                "Stored sources cannot replace the official source."
            }
        }
        val subscriptions = listOf(saved.firstOrNull { it.id == OFFICIAL_SOURCE_ID } ?: official) +
            saved.filterNot { it.id == OFFICIAL_SOURCE_ID }
        val bundled = parser.parse(bundledOfficialJson, false)
        val snapshots = subscriptions.mapNotNull { source ->
            val cached = stored.cachedCatalogs[source.id]?.let { parser.parse(it, source.allowHttp) }
            (cached ?: bundled.takeIf { source.id == OFFICIAL_SOURCE_ID })?.let {
                CatalogSnapshot(source, it, fromCache = cached != null)
            }
        }
        val installed = stored.installed
        require(installed.size <= 256 && installed.distinctBy { it.key }.size == installed.size) { "Invalid stored installations." }
        installed.forEach { installation ->
            parser.validate(PluginCatalog(PLUGIN_CATALOG_SCHEMA_VERSION, "stored", "Stored", "",
                installation.plugin.updatedAt, listOf(installation.plugin)), installation.allowHttp)
            require(installation.release in installation.plugin.availableVersions) { "Stored installation has no matching release." }
        }
        updateFloors = stored.updateFloors
        require(updateFloors.values.all { it >= 1 }) { "Invalid stored mandatory version floors." }
        val settings = preferences.currentSettings()
        val floors = nextUpdateFloors(snapshots)
        persistAndPublish(CatalogWrite(subscriptions = subscriptions, updateFloors = floors)) {
            updateFloors = floors
            mutableState.value = PluginRepositoryState(true, subscriptions = subscriptions, snapshots = snapshots,
                installed = installed, selectedKind = settings.selectedKind, showNsfw = settings.showNsfw)
        }
    }

    suspend fun refresh() = operations.withLock {
        check(mutableState.value.initialized) { "Plugin repository is not initialized." }
        mutableState.update { it.copy(refreshing = true, error = null) }
        try {
            cleanupPluginData(records.read().pendingDataDeletes)
            for (subscription in mutableState.value.subscriptions.filter { it.enabled }) {
                val previous = mutableState.value.snapshots.firstOrNull { it.subscription.id == subscription.id }
                try {
                    val catalog = fetch(subscription)
                    currentCoroutineContext().ensureActive()
                    val snapshot = CatalogSnapshot(subscription, catalog)
                    val next = mutableState.value.snapshots.filterNot { it.subscription.id == subscription.id } + snapshot
                    val floors = nextUpdateFloors(next)
                    persistAndPublish(CatalogWrite(cachedCatalogs = mapOf(subscription.id to PluginJson.encodeToString(catalog)),
                        updateFloors = floors)) {
                        updateFloors = floors
                        mutableState.update { it.copy(snapshots = next) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val message = failure.message ?: failure.toString()
                    val snapshots = mutableState.value.snapshots.filterNot { it.subscription.id == subscription.id } +
                        listOfNotNull(previous?.copy(error = message.take(2000), fromCache = true))
                    mutableState.update { it.copy(snapshots = snapshots, error = message.take(2000)) }
                }
            }
        } finally {
            mutableState.update { it.copy(refreshing = false) }
        }
    }

    suspend fun addSource(url: String, allowHttp: Boolean) = operations.withLock {
        val checked = PluginUrlPolicy.check(url.trim(), allowHttp).toString()
        require(mutableState.value.subscriptions.none { it.url == checked }) { "This plugin source is already subscribed." }
        require(mutableState.value.subscriptions.size < 16) { "At most 16 plugin sources can be subscribed." }
        var id = "custom.${Clock.System.now().toEpochMilliseconds()}"
        while (mutableState.value.subscriptions.any { it.id == id }) id += "1"
        val source = CatalogSubscription(id = id, url = checked, allowHttp = allowHttp)
        val catalog = fetch(source)
        currentCoroutineContext().ensureActive()
        val subscriptions = mutableState.value.subscriptions + source
        val snapshots = mutableState.value.snapshots + CatalogSnapshot(source, catalog)
        val floors = nextUpdateFloors(snapshots)
        persistAndPublish(CatalogWrite(subscriptions = subscriptions,
            cachedCatalogs = mapOf(source.id to PluginJson.encodeToString(catalog)), updateFloors = floors)) {
            updateFloors = floors
            mutableState.update { it.copy(subscriptions = subscriptions, snapshots = snapshots, error = null) }
        }
    }

    suspend fun removeSource(sourceId: String) = operations.withLock {
        require(sourceId != OFFICIAL_SOURCE_ID) { "The built-in official source cannot be removed." }
        val subscriptions = mutableState.value.subscriptions.filterNot { it.id == sourceId }
        val installed = mutableState.value.installed.filterNot { it.sourceId == sourceId }
        val floors = updateFloors.filterKeys { !it.startsWith("$sourceId:") }
        persistAndPublish(CatalogWrite(subscriptions = subscriptions, installed = installed,
            removeCachedCatalogs = setOf(sourceId), updateFloors = floors)) {
            updateFloors = floors
            mutableState.update { it.copy(subscriptions = subscriptions,
                snapshots = it.snapshots.filterNot { snapshot -> snapshot.subscription.id == sourceId }, installed = installed) }
        }
    }

    suspend fun setSourceEnabled(sourceId: String, enabled: Boolean) = operations.withLock {
        val subscriptions = mutableState.value.subscriptions.map { if (it.id == sourceId) it.copy(enabled = enabled) else it }
        persistAndPublish(CatalogWrite(subscriptions = subscriptions)) {
            mutableState.update { it.copy(subscriptions = subscriptions, snapshots = it.snapshots.map { snapshot ->
                snapshot.copy(subscription = subscriptions.first { source -> source.id == snapshot.subscription.id })
            }) }
        }
    }

    suspend fun selectKind(kind: MediaKind) = operations.withLock {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            preferences.selectKind(kind)
            mutableState.update { it.copy(selectedKind = kind) }
        }
    }

    suspend fun setShowNsfw(show: Boolean) = operations.withLock {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            preferences.setShowNsfw(show)
            mutableState.update { it.copy(showNsfw = show) }
        }
    }

    fun releaseBlockReason(listing: CatalogListing, release: PluginRelease): String? {
        val current = state.value.listingByKey[listing.key] ?: return "The plugin source is removed or disabled."
        if (release !in current.plugin.availableVersions || !release.usable) return "This plugin release is no longer usable or its metadata changed."
        if (current.plugin.nsfw && !state.value.showNsfw) return "This plugin is marked NSFW; enable that preference in More before opening it."
        if (state.value.selectedKind !in release.kinds) return "This release does not support the selected content type."
        val floor = maxOf(current.plugin.minimumUsableVersionCode, updateFloors[listing.key] ?: 1,
            updateFloors["$OFFICIAL_SOURCE_ID:${current.plugin.id}"] ?: 1,
            if (current.plugin.forceUpdate) current.plugin.latestRelease.versionCode else 1)
        if (release.versionCode < floor) return "A mandatory plugin update is required."
        if (host.appVersionCode < release.minimumHostVersionCode ||
            (release.maximumHostVersionCode != null && host.appVersionCode > release.maximumHostVersionCode)) {
            return "This plugin release does not support application ${host.appVersion}."
        }
        return release.protocol.incompatibility(host)
    }

    fun recommendedRelease(listing: CatalogListing): PluginRelease? =
        listing.plugin.availableVersions.sortedByDescending { it.versionCode }.firstOrNull { releaseBlockReason(listing, it) == null }

    fun installationBlockReason(installation: InstalledPlugin): String? {
        val listing = state.value.listingByKey[installation.key]
            ?: return "Refresh or enable this plugin source before opening its installation."
        val current = listing.plugin.availableVersions.firstOrNull { it.versionCode == installation.release.versionCode }
            ?: return "The installed release was removed from its plugin source."
        if (current != installation.release) return "Release metadata changed; review and reinstall this plugin."
        return releaseBlockReason(listing, current)
    }

    suspend fun recordInstallation(listing: CatalogListing, release: PluginRelease, verified: PluginDescriptor) = operations.withLock {
        require(releaseBlockReason(listing, release) == null) { releaseBlockReason(listing, release).orEmpty() }
        val current = requireNotNull(state.value.listingByKey[listing.key])
        require(verified.id == current.plugin.id && verified.version == release.version && verified.versionCode == release.versionCode &&
            verified.permissions == release.permissions && verified.protocol == release.protocol &&
            verified.kinds == release.kinds && verified.languages == release.languages) {
            "The signed plugin descriptor does not match its catalog release."
        }
        if (mutableState.value.installed.any { it.key == current.key && it.release == release }) return@withLock
        val installation = InstalledPlugin(current.snapshot.subscription.id, current.snapshot.subscription.url,
            current.plugin, release, Clock.System.now().toString(), current.snapshot.subscription.allowHttp)
        val installed = mutableState.value.installed.filterNot { it.key == installation.key } + installation
        require(installed.size <= 256) { "At most 256 plugins can be installed." }
        require(current.plugin.id !in records.read().pendingDataDeletes) { "Plugin data cleanup is incomplete; refresh before installing." }
        persistAndPublish(CatalogWrite(installed = installed)) {
            mutableState.update { it.copy(installed = installed) }
        }
    }

    suspend fun uninstall(key: String, eraseData: Boolean) = operations.withLock {
        val previous = mutableState.value.installed.firstOrNull { it.key == key }
        if (previous == null) {
            cleanupPluginData(records.read().pendingDataDeletes)
            return@withLock
        }
        val installed = mutableState.value.installed.filterNot { it.key == key }
        val deletedIds = if (eraseData && installed.none { it.plugin.id == previous.plugin.id }) setOf(previous.plugin.id) else emptySet()
        // A Room deletion record makes DataStore cleanup retryable without pretending the two stores share a transaction.
        persistAndPublish(CatalogWrite(installed = installed, pendingDataDeletes = deletedIds)) {
            mutableState.update { it.copy(installed = installed) }
        }
        cleanupPluginData(deletedIds)
    }

    suspend fun prepareInstallation() = operations.withLock { cleanupPluginData(records.read().pendingDataDeletes) }

    private suspend fun cleanupPluginData(pluginIds: Set<String>) {
        if (pluginIds.isEmpty()) return
        pluginIds.forEach { preferences.erasePluginData(it) }
        records.write(CatalogWrite(completedDataDeletes = pluginIds))
    }

    private suspend fun persistAndPublish(change: CatalogWrite, publish: () -> Unit) {
        currentCoroutineContext().ensureActive()
        // Cancellation cannot leave live state behind a database transaction that already committed.
        withContext(NonCancellable) {
            records.write(change)
            publish()
        }
    }

    private suspend fun fetch(subscription: CatalogSubscription): PluginCatalog {
        val url = PluginUrlPolicy.check(subscription.url, subscription.allowHttp).toString()
        val response = transport.fetch(url, mapOf("Accept" to "application/json"), 2 * 1024 * 1024)
        require(response.statusCode in 200..299) { "Plugin catalog request failed: HTTP ${response.statusCode}." }
        return parser.parse(response.bytes.decodeToString(throwOnInvalidSequence = true), subscription.allowHttp)
    }

    private fun nextUpdateFloors(snapshots: List<CatalogSnapshot>): Map<String, Long> {
        val next = updateFloors.toMutableMap()
        snapshots.forEach { snapshot -> snapshot.catalog.plugins.forEach { plugin ->
            val key = "${snapshot.subscription.id}:${plugin.id}"
            val minimum = if (plugin.forceUpdate) plugin.latestRelease.versionCode else plugin.minimumUsableVersionCode
            next[key] = maxOf(next[key] ?: 1, minimum)
        } }
        return next
    }

}
