package crow.evilo.plugin.catalog

import crow.evilo.model.MediaKind
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.plugin.api.PluginDescriptor
import crow.evilo.plugin.api.PluginJson
import crow.evilo.plugin.subscription.SubscriptionContent
import crow.evilo.plugin.subscription.SubscriptionIndex
import crow.evilo.plugin.subscription.SubscriptionParser
import crow.evilo.plugin.subscription.SubscriptionPointer
import crow.evilo.plugin.subscription.indexAsCatalog
import crow.evilo.network.ktor.BoundedHttpTransport
import crow.evilo.network.ktor.HttpRedirectRejectedException
import crow.evilo.network.ktor.HttpResponseLimitException
import crow.evilo.network.ktor.HttpTransportException
import crow.evilo.network.ktor.HttpTransportTimeoutException
import crow.evilo.network.ktor.PluginUrlPolicy
import crow.evilo.storage.CatalogWrite
import crow.evilo.storage.EviloPreferencesRepository
import crow.evilo.storage.PluginCatalogStore
import crow.wasmline.loader.WasmlineTrustedKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
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
    val mergeDuplicatePlugins: Boolean = true,
    val preferredInstallations: Map<String, String> = emptyMap(),
    val sourceChecks: Map<String, SourceCheckState> = emptyMap(),
) {
    val catalogPluginByKey: Map<String, CatalogPlugin> by lazy { snapshots.flatMap { snapshot ->
        snapshot.catalog.plugins.map { "${snapshot.subscription.id}:${it.id}" to it }
    }.toMap() }
    val listings: List<CatalogListing> by lazy { snapshots.filter { it.subscription.enabled }.flatMap { snapshot ->
        snapshot.catalog.plugins.map { CatalogListing(snapshot, it) }
    } }
    val listingByKey: Map<String, CatalogListing> by lazy { listings.associateBy { it.key } }
    private val nsfwPluginIds by lazy { installed.filter { it.plugin.nsfw || catalogPluginByKey[it.key]?.nsfw == true }
        .map { it.plugin.id }.toSet() }

    fun isPluginNsfw(pluginId: String): Boolean = pluginId in nsfwPluginIds
}

class PluginCatalogRepository(
    private val records: PluginCatalogStore,
    private val preferences: EviloPreferencesRepository,
    private val transport: BoundedHttpTransport,
    val host: HostDescriptor,
    private val bundledUrl: String = DEFAULT_BUNDLED_CATALOG_URL,
    private val officialKeys: WasmlineTrustedKeys = WasmlineTrustedKeys { _, _ -> null },
) {
    private val parser = CatalogParser()
    private val operations = Mutex()
    private val settingOperations = Mutex()
    private val mutableState = MutableStateFlow(PluginRepositoryState())
    val state = mutableState.asStateFlow()
    private var updateFloors = emptyMap<String, Long>()

    fun dismissError() { mutableState.update { it.copy(error = null) } }

    fun officialRelease(release: PluginRelease): Boolean = isOfficialRelease(release, officialKeys)

    private fun dataKey(pluginId: String, release: PluginRelease): String = publisherStorageKey(pluginId, release, officialKeys)

    suspend fun initialize(bundledJson: String) = operations.withLock {
        if (mutableState.value.initialized) return@withLock
        PluginUrlPolicy.check(bundledUrl)
        val bundledSource = CatalogSubscription(BUNDLED_SOURCE_ID, bundledUrl)
        val stored = records.read()
        cleanupPluginData(stored.pendingDataDeletes)
        // Rename the built-in subscription without losing its installations, cache, or version floors.
        val saved = stored.subscriptions.map { source ->
            if (source.id == LEGACY_BUNDLED_SOURCE_ID) source.copy(id = BUNDLED_SOURCE_ID, url = migrateBundledUrl(source.url))
            else source
        }
        require(saved.distinctBy { it.id }.size == saved.size &&
            saved.distinctBy { it.url }.size == saved.size) { "Invalid stored plugin subscriptions." }
        saved.forEach {
            PluginUrlPolicy.check(it.url, it.allowHttp)
            require(it.id != BUNDLED_SOURCE_ID || (it.url == bundledUrl && !it.allowHttp)) {
                "Stored sources cannot replace the built-in source."
            }
        }
        val subscriptions = listOf(saved.firstOrNull { it.id == BUNDLED_SOURCE_ID } ?: bundledSource) +
            saved.filterNot { it.id == BUNDLED_SOURCE_ID }
        val bundled = parser.parse(bundledJson, false)
        val legacyCache = stored.cachedCatalogs[LEGACY_BUNDLED_SOURCE_ID]
        val snapshots = subscriptions.mapNotNull { source ->
            val cachedJson = stored.cachedCatalogs[source.id] ?: legacyCache.takeIf { source.id == BUNDLED_SOURCE_ID }
            val cached = cachedJson?.let { parser.parse(it, source.allowHttp) }?.let { catalog ->
                if (source.id == BUNDLED_SOURCE_ID && catalog.id == LEGACY_BUNDLED_CATALOG_ID)
                    catalog.copy(id = bundled.id, name = bundled.name, description = bundled.description)
                else catalog
            }
            (cached ?: bundled.takeIf { source.id == BUNDLED_SOURCE_ID })?.let {
                CatalogSnapshot(source, it, fromCache = cached != null)
            }
        }
        val installed = stored.installed.map { installation ->
            if (installation.sourceId == LEGACY_BUNDLED_SOURCE_ID)
                installation.copy(sourceId = BUNDLED_SOURCE_ID, sourceUrl = migrateBundledUrl(installation.sourceUrl))
            else installation
        }
        require(installed.distinctBy { it.key }.size == installed.size) { "Invalid stored installations." }
        installed.forEach { installation ->
            parser.validate(PluginCatalog(PLUGIN_CATALOG_SCHEMA_VERSION, "stored", "Stored", "",
                installation.plugin.updatedAt, listOf(installation.plugin)), installation.allowHttp)
            require(installation.release in installation.plugin.availableVersions) { "Stored installation has no matching release." }
            installation.approvedManifestSha256?.let { digest ->
                require(Regex("[0-9a-f]{64}").matches(digest) &&
                    (installation.release.manifestSha256 == null || installation.release.manifestSha256 == digest)) {
                    "Stored manifest approval does not match the installed release."
                }
            }
        }
        require(stored.updateFloors.values.all { it >= 1 }) { "Invalid stored mandatory version floors." }
        updateFloors = buildMap {
            stored.updateFloors.forEach { (key, versionCode) ->
                val migratedKey = if (key.startsWith("$LEGACY_BUNDLED_SOURCE_ID:")) "$BUNDLED_SOURCE_ID:${key.substringAfter(':')}" else key
                put(migratedKey, maxOf(get(migratedKey) ?: 1, versionCode))
            }
        }
        val storedSettings = preferences.currentSettings()
        val installedIds = installed.map { it.plugin.id }.toSet()
        val settings = if (storedSettings.preferredInstallations.keys.any { it !in installedIds }) {
            preferences.retainPreferredInstallations(installedIds)
            preferences.currentSettings()
        } else storedSettings
        val floors = nextUpdateFloors(snapshots)
        val migratedCache = if (legacyCache == null) emptyMap() else snapshots.filter { it.subscription.id == BUNDLED_SOURCE_ID }
            .associate { it.subscription.id to PluginJson.encodeToString(it.catalog) }
        persistAndPublish(CatalogWrite(subscriptions = subscriptions, cachedCatalogs = migratedCache,
            removeCachedCatalogs = setOf(LEGACY_BUNDLED_SOURCE_ID), installed = installed.takeIf { it != stored.installed },
            updateFloors = floors)) {
            updateFloors = floors
            mutableState.value = PluginRepositoryState(true, subscriptions = subscriptions, snapshots = snapshots,
                installed = installed, selectedKind = settings.selectedKind, showNsfw = settings.showNsfw,
                mergeDuplicatePlugins = settings.mergeDuplicatePlugins, preferredInstallations = settings.preferredInstallations,
                sourceChecks = snapshots.associate { it.subscription.id to SourceCheckState(usingCache = it.fromCache) })
        }
    }

    /**
     * Fetches every enabled source, in bounded parallel batches.
     *
     * The mutex guards persistence and published state only: a fetch waits on a remote
     * server, so holding the mutex across it would block every install, subscription,
     * and uninstall behind the slowest server in the list.
     */
    suspend fun refresh(sourceId: String? = null) {
        val sources = operations.withLock {
            check(mutableState.value.initialized) { "Plugin repository is not initialized." }
            val selected = mutableState.value.subscriptions.filter { it.enabled && (sourceId == null || it.id == sourceId) }
            require(sourceId == null || selected.isNotEmpty()) { "The plugin source is removed or disabled." }
            mutableState.update { it.copy(refreshing = true, error = null) }
            selected
        }
        try {
            fetchInBatches(sources)
        } finally {
            operations.withLock { mutableState.update { it.copy(refreshing = false) } }
        }
    }

    /** Fetches each source in bounded batches, publishing successes and recording failures. */
    private suspend fun fetchInBatches(sources: List<CatalogSubscription>) {
        cleanupPluginData(records.read().pendingDataDeletes)
        sources.chunked(REFRESH_CONCURRENCY).forEach { batch ->
            operations.withLock {
                mutableState.update { current ->
                    current.copy(sourceChecks = current.sourceChecks + batch.associate { subscription ->
                        subscription.id to (current.sourceChecks[subscription.id] ?: SourceCheckState()).copy(running = true, error = null)
                    })
                }
            }
            val results = coroutineScope {
                batch.map { subscription -> async { subscription to fetchOutcome(subscription) } }.awaitAll()
            }
            operations.withLock {
                val cancelled = currentCoroutineContext().let { context ->
                    runCatching { context.ensureActive() }.isFailure
                }
                if (cancelled) return@withLock
                for ((subscription, outcome) in results) applyRefresh(subscription, outcome)
            }
        }
    }

    private suspend fun fetchOutcome(subscription: CatalogSubscription): Result<PluginCatalog> = try {
        Result.success(fetch(subscription))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private suspend fun applyRefresh(subscription: CatalogSubscription, outcome: Result<PluginCatalog>) {
        val previous = mutableState.value.snapshots.firstOrNull { it.subscription.id == subscription.id }
        val previousCheck = mutableState.value.sourceChecks[subscription.id] ?: SourceCheckState()
        outcome.fold(
            onSuccess = { catalog ->
                val snapshot = CatalogSnapshot(subscription, catalog)
                val next = mutableState.value.snapshots.filterNot { it.subscription.id == subscription.id } + snapshot
                val floors = nextUpdateFloors(next)
                persistAndPublish(CatalogWrite(cachedCatalogs = mapOf(subscription.id to PluginJson.encodeToString(catalog)),
                    updateFloors = floors)) {
                    updateFloors = floors
                    mutableState.update { it.copy(snapshots = next, sourceChecks = it.sourceChecks +
                        (subscription.id to SourceCheckState(lastSuccessAt = Clock.System.now().toString()))) }
                }
            },
            onFailure = { failure ->
                val message = failure.message ?: failure.toString()
                val snapshots = mutableState.value.snapshots.filterNot { it.subscription.id == subscription.id } +
                    listOfNotNull(previous?.copy(error = message.take(2000), fromCache = true))
                mutableState.update { it.copy(snapshots = snapshots, error = message.take(2000), sourceChecks = it.sourceChecks +
                    (subscription.id to previousCheck.copy(running = false, error = message.take(2000), usingCache = previous != null))) }
            },
        )
    }

    /**
     * Adds one address. A plugin catalog and a signed pointer both become one source: a
     * pointer's index already lists every plugin, so no address needs a request per plugin.
     */
    suspend fun addSource(url: String, allowHttp: Boolean) {
        val checked = PluginUrlPolicy.check(url.trim(), allowHttp).toString()
        operations.withLock {
            require(mutableState.value.subscriptions.none { it.url == checked }) { "This plugin source is already subscribed." }
        }
        // Network work stays outside the mutex, as in refresh.
        val text = download(checked, allowHttp)
        val catalog = when (val content = classifySafely(text, allowHttp)) {
            is SubscriptionContent.Pointer -> fetchIndex(content.document, allowHttp)
            else -> parseCatalog(text, allowHttp)
        }
        operations.withLock { addCatalogSource(checked, allowHttp, catalog) }
    }

    private suspend fun addCatalogSource(url: String, allowHttp: Boolean, catalog: PluginCatalog) {
        val source = CatalogSubscription(newSourceId(url), url, allowHttp)
        publishSources(listOf(source), mapOf(source.id to catalog))
    }

    /** Adds the sources, their catalogs, and their version floors in one write. */
    private suspend fun publishSources(sources: List<CatalogSubscription>, catalogs: Map<String, PluginCatalog>) {
        val subscriptions = mutableState.value.subscriptions + sources
        val snapshots = mutableState.value.snapshots + sources.mapNotNull { source ->
            catalogs[source.id]?.let { CatalogSnapshot(source, it) }
        }
        val floors = nextUpdateFloors(snapshots)
        val now = Clock.System.now().toString()
        persistAndPublish(CatalogWrite(subscriptions = subscriptions,
            cachedCatalogs = catalogCache(catalogs), updateFloors = floors)) {
            updateFloors = floors
            mutableState.update { current ->
                current.copy(subscriptions = subscriptions, snapshots = snapshots, error = null,
                    sourceChecks = current.sourceChecks + sources.associate { source ->
                        source.id to SourceCheckState(lastSuccessAt = if (source.id in catalogs) now else null)
                    })
            }
        }
    }

    private fun catalogCache(catalogs: Map<String, PluginCatalog>): Map<String, String> =
        catalogs.mapValues { PluginJson.encodeToString(it.value) }

    /** A stable, separator-free identity: the installation key joins id and plugin id with ':'. */
    private fun newSourceId(url: String): String {
        val base = "custom." + url.encodeUtf8().sha256().hex().take(24)
        var candidate = base
        var suffix = 1
        while (mutableState.value.subscriptions.any { it.id == candidate }) candidate = "$base-$suffix".also { suffix++ }
        return candidate
    }

    suspend fun removeSource(sourceId: String) = operations.withLock {
        require(sourceId != BUNDLED_SOURCE_ID) { "The built-in source cannot be removed; disable it instead." }
        pinDefaultSources(state.value.installed.filter { it.sourceId == sourceId }.map { it.key }.toSet())
        val subscriptions = mutableState.value.subscriptions.filterNot { it.id == sourceId }
        val installed = mutableState.value.installed.filterNot { it.sourceId == sourceId }
        val floors = updateFloors.filterKeys { !it.startsWith("$sourceId:") }
        persistAndPublish(CatalogWrite(subscriptions = subscriptions, installed = installed,
            removeCachedCatalogs = setOf(sourceId), updateFloors = floors)) {
            updateFloors = floors
            mutableState.update { it.copy(subscriptions = subscriptions,
                snapshots = it.snapshots.filterNot { snapshot -> snapshot.subscription.id == sourceId }, installed = installed,
                sourceChecks = it.sourceChecks - sourceId) }
        }
        prunePreferredSources()
    }

    suspend fun setSourceEnabled(sourceId: String, enabled: Boolean) = operations.withLock {
        require(mutableState.value.subscriptions.any { it.id == sourceId }) { "This plugin source is no longer available." }
        if (!enabled) pinDefaultSources(state.value.installed.filter { it.sourceId == sourceId }.map { it.key }.toSet())
        val subscriptions = mutableState.value.subscriptions.map { if (it.id == sourceId) it.copy(enabled = enabled) else it }
        persistAndPublish(CatalogWrite(subscriptions = subscriptions)) {
            mutableState.update { it.copy(subscriptions = subscriptions, snapshots = it.snapshots.map { snapshot ->
                snapshot.copy(subscription = subscriptions.first { source -> source.id == snapshot.subscription.id })
            }) }
        }
    }

    suspend fun selectKind(kind: MediaKind) = settingOperations.withLock {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            preferences.selectKind(kind)
            mutableState.update { it.copy(selectedKind = kind) }
        }
    }

    suspend fun setShowNsfw(show: Boolean) = settingOperations.withLock {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            preferences.setShowNsfw(show)
            mutableState.update { it.copy(showNsfw = show) }
        }
    }

    suspend fun setMergeDuplicatePlugins(merge: Boolean) = settingOperations.withLock {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            preferences.setMergeDuplicatePlugins(merge)
            mutableState.update { it.copy(mergeDuplicatePlugins = merge) }
        }
    }

    suspend fun selectPreferredInstallation(key: String) = settingOperations.withLock {
        val installation = state.value.installed.firstOrNull { it.key == key } ?: error("This plugin was uninstalled.")
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            preferences.setPreferredInstallation(installation.plugin.id, key)
            val settings = preferences.currentSettings()
            mutableState.update { it.copy(preferredInstallations = settings.preferredInstallations) }
        }
    }

    // Source changes and replacement timestamps must not silently move an implicit display default.
    private suspend fun pinDefaultSources(affectedKeys: Set<String>) = settingOperations.withLock {
        val sourceOrder = state.value.subscriptions.mapIndexed { index, source -> source.id to index }.toMap()
        val defaults = state.value.installed.groupBy { it.plugin.id }.filter { (id, copies) ->
            copies.size > 1 && id !in state.value.preferredInstallations
        }.mapValues { (_, installations) ->
            installations.minWith(compareBy<InstalledPlugin> { installationOrderTime(it.installedAt) }
                .thenBy { sourceOrder[it.sourceId] ?: Int.MAX_VALUE }.thenBy { it.key }).key
        }.filterValues { it in affectedKeys }
        if (defaults.isEmpty()) return@withLock
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            preferences.pinDefaultInstallations(defaults, state.value.installed.map { it.plugin.id }.toSet())
            val settings = preferences.currentSettings()
            mutableState.update { it.copy(preferredInstallations = settings.preferredInstallations) }
        }
    }

    private suspend fun prunePreferredSources() = settingOperations.withLock {
        val installedIds = state.value.installed.map { it.plugin.id }.toSet()
        if (state.value.preferredInstallations.keys.all { it in installedIds }) return@withLock
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            preferences.retainPreferredInstallations(installedIds)
            val settings = preferences.currentSettings()
            mutableState.update { it.copy(preferredInstallations = settings.preferredInstallations) }
        }
    }

    fun releaseBlockReason(listing: CatalogListing, release: PluginRelease): String? = releaseBlock(listing, release)?.message

    fun releaseBlock(listing: CatalogListing, release: PluginRelease): PluginBlock? {
        val current = state.value.listingByKey[listing.key] ?: return PluginBlock(
            if (state.value.subscriptions.any { it.id == listing.snapshot.subscription.id && !it.enabled })
                PluginBlockCode.SOURCE_DISABLED else PluginBlockCode.SOURCE_MISSING, "The plugin source is removed or disabled.")
        if (release !in current.plugin.availableVersions) return PluginBlock(PluginBlockCode.METADATA_CHANGED,
            "This plugin release is no longer usable or its metadata changed.")
        if (state.value.installed.any { it.plugin.id == current.plugin.id &&
                dataKey(it.plugin.id, it.release) != dataKey(current.plugin.id, release) })
            return PluginBlock(PluginBlockCode.METADATA_CHANGED,
                "A different publisher already owns this installed plugin ID. Uninstall its copies before changing the signing identity.")
        val pinned = state.value.installed.firstOrNull { it.key == current.key && it.release.versionCode == release.versionCode }
        if (pinned?.approvedManifestSha256 != null && release.manifestSha256 != null &&
            pinned.approvedManifestSha256 != release.manifestSha256)
            return PluginBlock(PluginBlockCode.METADATA_CHANGED, "The published manifest changed without a new version. Ask the publisher for a new release.")
        if (!release.usable) return PluginBlock(PluginBlockCode.RELEASE_WITHDRAWN, "This plugin release is no longer usable.")
        if ((current.plugin.nsfw || state.value.isPluginNsfw(current.plugin.id)) && !state.value.showNsfw)
            return PluginBlock(PluginBlockCode.NSFW_HIDDEN, "This plugin is marked NSFW; enable that preference in Settings before opening it.")
        if (state.value.selectedKind !in release.kinds) return PluginBlock(PluginBlockCode.KIND_UNSUPPORTED,
            "This release does not support the selected content type.")
        val floor = maxOf(current.plugin.minimumUsableVersionCode, updateFloors[listing.key] ?: 1,
            if (current.plugin.forceUpdate) current.plugin.latestRelease.versionCode else 1)
        if (release.versionCode < floor) return PluginBlock(PluginBlockCode.MANDATORY_UPDATE, "A mandatory plugin update is required.")
        if (host.appVersionCode < release.minimumHostVersionCode ||
            (release.maximumHostVersionCode != null && host.appVersionCode > release.maximumHostVersionCode)) {
            return PluginBlock(PluginBlockCode.HOST_INCOMPATIBLE, "This plugin release does not support application ${host.appVersion}.")
        }
        return null
    }

    fun recommendedRelease(listing: CatalogListing): PluginRelease? =
        listing.plugin.availableVersions.sortedByDescending { it.versionCode }.firstOrNull { releaseBlockReason(listing, it) == null }

    fun installationBlockReason(installation: InstalledPlugin): String? = installationBlock(installation)?.message

    fun installationBlock(installation: InstalledPlugin): PluginBlock? {
        if (installation.approvedManifestSha256 == null) return PluginBlock(PluginBlockCode.METADATA_CHANGED,
            "Review and reinstall this legacy installation to pin its signed manifest.")
        val listing = state.value.listingByKey[installation.key]
            ?: return PluginBlock(when {
                state.value.subscriptions.any { it.id == installation.sourceId && !it.enabled } -> PluginBlockCode.SOURCE_DISABLED
                state.value.snapshots.any { it.subscription.id == installation.sourceId } -> PluginBlockCode.RELEASE_WITHDRAWN
                else -> PluginBlockCode.SOURCE_MISSING
            }, "Refresh or enable this plugin source before opening its installation.")
        val current = listing.plugin.availableVersions.firstOrNull { it.versionCode == installation.release.versionCode }
            ?: return PluginBlock(PluginBlockCode.RELEASE_WITHDRAWN, "The installed release was removed from its plugin source.")
        if (current != installation.release) return PluginBlock(PluginBlockCode.METADATA_CHANGED, "Release metadata changed; review and reinstall this plugin.")
        return releaseBlock(listing, current)
    }

    suspend fun recordInstallation(listing: CatalogListing, release: PluginRelease, verified: PluginDescriptor,
        verifiedManifestSha256: String? = null) = operations.withLock {
        require(releaseBlockReason(listing, release) == null) { releaseBlockReason(listing, release).orEmpty() }
        val current = requireNotNull(state.value.listingByKey[listing.key])
        require(verified.id == current.plugin.id && verified.version == release.version && verified.versionCode == release.versionCode &&
            verified.kinds == release.kinds && verified.languages == release.languages) {
            "The signed plugin descriptor does not match its catalog release."
        }
        val previous = mutableState.value.installed.firstOrNull { it.key == current.key }
        require(verifiedManifestSha256 != null && Regex("[0-9a-f]{64}").matches(verifiedManifestSha256) &&
            (release.manifestSha256 == null || release.manifestSha256 == verifiedManifestSha256)) { "Installation requires the verified signed manifest digest." }
        require(previous == null || previous.release.versionCode != release.versionCode ||
            previous.approvedManifestSha256 == null || previous.approvedManifestSha256 == verifiedManifestSha256) {
            "The signed manifest changed without a new version."
        }
        if (previous?.release == release && previous.approvedManifestSha256 == verifiedManifestSha256) return@withLock
        if (previous != null) pinDefaultSources(setOf(current.key))
        val retainedTimestamp = previous?.takeIf {
            it.release.copy(signing = release.signing, manifestSha256 = release.manifestSha256) == release &&
                dataKey(it.plugin.id, it.release) == dataKey(current.plugin.id, release) &&
                (it.approvedManifestSha256 == null || it.approvedManifestSha256 == verifiedManifestSha256)
        }?.installedAt
        val installation = InstalledPlugin(current.snapshot.subscription.id, current.snapshot.subscription.url,
            current.plugin, release, retainedTimestamp ?: Clock.System.now().toString(),
            current.snapshot.subscription.allowHttp, verifiedManifestSha256)
        val installed = mutableState.value.installed.filterNot { it.key == installation.key } + installation
        require(dataKey(current.plugin.id, release) !in records.read().pendingDataDeletes) { "Plugin data cleanup is incomplete; refresh before installing." }
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
        pinDefaultSources(setOf(key))
        val installed = mutableState.value.installed.filterNot { it.key == key }
        val deletedIds = if (eraseData && installed.none { it.plugin.id == previous.plugin.id }) setOf(dataKey(previous.plugin.id, previous.release)) else emptySet()
        // A Room deletion record makes DataStore cleanup retryable without pretending the two stores share a transaction.
        persistAndPublish(CatalogWrite(installed = installed, pendingDataDeletes = deletedIds)) {
            mutableState.update { it.copy(installed = installed) }
        }
        prunePreferredSources()
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

    /**
     * Reads one source. An address may serve a plugin catalog, a hand-maintained URL
     * collection, or a signed subscription pointer; the document says which.
     */
    private suspend fun fetch(subscription: CatalogSubscription): PluginCatalog {
        val url = PluginUrlPolicy.check(subscription.url, subscription.allowHttp).toString()
        val text = download(url, subscription.allowHttp)
        return when (val content = classifySafely(text, subscription.allowHttp)) {
            is SubscriptionContent.PluginCatalog -> parseCatalog(text, subscription.allowHttp)
            is SubscriptionContent.Pointer -> fetchIndex(content.document, subscription.allowHttp)
            null -> parseCatalog(text, subscription.allowHttp)
        }
    }

    private fun classifySafely(text: String, allowHttp: Boolean): SubscriptionContent? = try {
        SubscriptionParser.classify(text, allowHttp)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        // Not every legal plugin catalog declares plugins at parse time in tests; fall through
        // to the catalog parser, whose message is the one the user needs.
        null
    }

    private suspend fun fetchIndex(pointer: SubscriptionPointer, allowHttp: Boolean): PluginCatalog {
        val signatureUrl = pointer.indexUrl + ".sig"
        val indexBytes = downloadBytes(pointer.indexUrl, allowHttp)
        val signature = download(signatureUrl, allowHttp).trim().let { hex ->
            if (!hex.matches(Regex("[0-9a-f]{128}"))) throw SourceFailure.Untrusted("the signature file is not 128 hexadecimal characters")
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        val publicKeyUrl = pointer.indexUrl + ".pub"
        val publicKey = download(publicKeyUrl, allowHttp).trim().let { hex ->
            if (!hex.matches(Regex("[0-9a-f]{64}"))) throw SourceFailure.Untrusted("the published key is not 64 hexadecimal characters")
            SubscriptionParser.publicKey(hex)
        }
        val index = try {
            SubscriptionParser.verifyIndex(pointer, indexBytes, signature, publicKey, allowHttp)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IllegalArgumentException) {
            throw SourceFailure.Untrusted(failure.message ?: "the index signature does not verify")
        }
        return indexCatalog(index, allowHttp)
    }

    /**
     * An index entry already holds the newest release, so the source is installable after
     * this one fetch. Older versions remain in the plugin's own catalog, which the detail
     * page links to through the recorded source URL.
     */
    private fun indexCatalog(index: SubscriptionIndex, allowHttp: Boolean): PluginCatalog =
        parser.validate(indexAsCatalog(index.plugins, index.name, index.updatedAt), allowHttp)

    private suspend fun download(url: String, allowHttp: Boolean): String =
        downloadBytes(url, allowHttp).decodeToString(throwOnInvalidSequence = true)

    private suspend fun downloadBytes(url: String, allowHttp: Boolean): ByteArray {
        val parsed = PluginUrlPolicy.check(url, allowHttp)
        val checked = parsed.toString()
        val target = "${parsed.protocol.name}://${parsed.host}${if (parsed.port == parsed.protocol.defaultPort) "" else ":${parsed.port}"}"
        val response = try {
            transport.fetch(checked, mapOf("Accept" to "application/json"), MAX_CATALOG_BYTES)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (redirect: HttpRedirectRejectedException) {
            throw SourceFailure.Redirect(redirect.statusCode)
        } catch (limit: HttpResponseLimitException) {
            throw SourceFailure.TooLarge(limit.maxBytes)
        } catch (timeout: HttpTransportTimeoutException) {
            throw SourceFailure.Timeout(target, timeout.timeoutMillis)
        } catch (unreachable: HttpTransportException) {
            throw SourceFailure.Unreachable(target, unreachable)
        } catch (failure: Exception) {
            throw SourceFailure.Unreachable(target, failure)
        }
        if (response.statusCode !in 200..299) throw SourceFailure.HttpStatus(target, response.statusCode)
        return response.bytes
    }

    private fun parseCatalog(text: String, allowHttp: Boolean): PluginCatalog = try {
        parser.parse(text, allowHttp)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (invalid: IllegalStateException) {
        throw SourceFailure.Invalid(invalid.message ?: "The document does not follow the catalog format.")
    } catch (notJson: Exception) {
        throw SourceFailure.NotJson(notJson)
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

    private fun migrateBundledUrl(url: String): String = if (url == LEGACY_BUNDLED_CATALOG_URL) bundledUrl else url

    private companion object {
        /** Parallel catalog fetches. Each source is one request, so a batch bounds memory and sockets. */
        const val REFRESH_CONCURRENCY = 6

        const val LEGACY_BUNDLED_SOURCE_ID = "official"
        const val LEGACY_BUNDLED_CATALOG_ID = "evilo.official"
        const val LEGACY_BUNDLED_CATALOG_URL =
            "https://raw.githubusercontent.com/crowforkotlin/evilo/main/plugin-repository/official.json"
    }
}
