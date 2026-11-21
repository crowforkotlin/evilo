package crow.evilo.app

import crow.evilo.model.MediaStream
import crow.evilo.plugin.catalog.CatalogPlugin
import crow.evilo.plugin.catalog.PluginRelease
import crow.evilo.plugin.catalog.publisherStorageKey
import crow.evilo.plugin.catalog.releaseVerificationKeys
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.plugin.api.MediaSource
import crow.evilo.plugin.api.PluginDescriptor
import crow.evilo.network.ktor.BoundedHttpTransport
import crow.evilo.network.ktor.PackageNetworkPolicy
import crow.evilo.network.ktor.PluginHttpClient
import crow.evilo.network.ktor.PluginNetworkPolicy
import crow.evilo.network.ktor.PolicyPackageNetworkClient
import crow.evilo.network.ktor.supportsConcurrentPluginRuntime
import crow.evilo.plugin.runtime.WasmlinePluginLoader
import crow.evilo.storage.EviloPreferencesRepository
import crow.evilo.storage.PluginManifestCache
import crow.evilo.storage.PluginStorageSession
import crow.evilo.storage.StoreLock
import crow.evilo.feature.reader.MediaSourceSession
import crow.evilo.feature.reader.PluginSessionClosedException
import crow.wasmline.WasmlineConfig
import crow.wasmline.loader.WasmlineLoadOptions
import crow.wasmline.loader.WasmlineTrustedKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PluginSessionFactory(
    private val host: HostDescriptor,
    private val preferences: EviloPreferencesRepository,
    private val trustedKeys: WasmlineTrustedKeys,
    private val transport: BoundedHttpTransport,
    private val manifests: PluginManifestCache,
) : PluginSessionConnector {
    override suspend fun connect(plugin: CatalogPlugin, release: PluginRelease, allowHttp: Boolean,
        onPhase: (PluginLoadPhase) -> Unit): MediaSourceSession {
        return connectVerified(plugin, release, allowHttp, release.manifestSha256, useCachedManifest = false, onPhase = onPhase)
    }

    override suspend fun connectInstalled(plugin: CatalogPlugin, release: PluginRelease, allowHttp: Boolean,
        approvedManifestSha256: String?, onPhase: (PluginLoadPhase) -> Unit): MediaSourceSession {
        require(approvedManifestSha256 != null) { "Review and reinstall this legacy installation to pin its signed manifest." }
        return connectVerified(plugin, release, allowHttp, approvedManifestSha256, useCachedManifest = true, onPhase = onPhase)
    }

    override suspend fun cacheVerifiedManifest(session: MediaSourceSession) {
        require(session is PluginMediaSession) { "The verified session must belong to the package loader." }
        withContext(Dispatchers.Default) { session.cacheVerifiedManifest() }
    }

    private suspend fun connectVerified(plugin: CatalogPlugin, release: PluginRelease, allowHttp: Boolean,
        expectedManifestSha256: String?, useCachedManifest: Boolean, onPhase: (PluginLoadPhase) -> Unit): MediaSourceSession {
        val callerContext = currentCoroutineContext()
        suspend fun report(phase: PluginLoadPhase) = withContext(callerContext) { onPhase(phase) }
        val expected = PluginDescriptor(plugin.id, plugin.name, release.version, release.versionCode, release.kinds,
            release.languages)
        val packagePolicy = PackageNetworkPolicy(release.manifestUrl, allowHttp)
        val policy = PluginNetworkPolicy(plugin.id, allowHttp)
        val store = preferences.pluginSession(plugin.id, publisherStorageKey(plugin.id, release, trustedKeys))
        val verificationKeys = releaseVerificationKeys(release, trustedKeys)
        val packages = CachedPackageClient(packagePolicy.manifestUrl,
            PolicyPackageNetworkClient(transport, packagePolicy), manifests,
            expectedManifestSha256.takeIf { useCachedManifest }, ::report)
        var manifestDigest: String? = null
        val http = PluginHttpClient(transport, policy)
        var loaded: MediaSource? = null
        try {
            onPhase(PluginLoadPhase.LOADING_PACKAGE)
            withContext(Dispatchers.Default) {
                store.transaction { loaded = WasmlinePluginLoader(host, store, http).loadRemote(
                    packagePolicy.manifestUrl,
                    WasmlineLoadOptions(runtimeConfig = WasmlineConfig(supportConcurrent = supportsConcurrentPluginRuntime), trustedKeys = verificationKeys,
                        networkClient = packages,
                        // Keep the loader's URL/TTL manifest cache disabled so both local and remote
                        // bytes always pass through our pinned identity check and signature verifier.
                        // The default loader cache still reuses content-addressed, hash-checked artifacts.
                        manifestTtlMs = 0, maxCacheBytes = 128L * 1024 * 1024, maxArtifactBytes = 64L * 1024 * 1024),
                    expected, release.sourceRevision, release.sourceArchiveSha256,
                    onNegotiating = { report(PluginLoadPhase.NEGOTIATING) },
                    expectedManifestSha256 = expectedManifestSha256,
                    onManifestChecked = { manifestDigest = it },
                ) }
            }
            currentCoroutineContext().ensureActive()
            return PluginMediaSession(BackgroundMediaSource(checkNotNull(loaded), store), http, checkNotNull(manifestDigest), packages)
        } catch (failure: Throwable) {
            try {
                withContext(NonCancellable + Dispatchers.Default) { loaded?.close() }
            } catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }
}

private class PluginMediaSession(
    override val source: MediaSource,
    private val http: PluginHttpClient,
    override val verifiedManifestSha256: String,
    private val packages: CachedPackageClient,
) : MediaSourceSession {
    private val mutex = Mutex()
    private val stateLock = StoreLock()
    private var closed = false

    suspend fun cacheVerifiedManifest() = mutex.withLock {
        ensureOpen()
        packages.cacheVerifiedManifest(verifiedManifestSha256)
    }

    override fun approveImageRequest(url: String, headers: Map<String, String>): crow.evilo.plugin.api.SourceRequest {
        ensureOpen()
        return http.approvedImageRequest(url, headers)
    }

    override suspend fun imageBytes(url: String, headers: Map<String, String>): ByteArray =
        withContext(Dispatchers.Default) {
            ensureOpen()
            val bytes = http.imageBytes(url, headers)
            currentCoroutineContext().ensureActive()
            ensureOpen()
            bytes
        }

    override fun externalStreamUrl(stream: MediaStream): String {
        ensureOpen()
        require(stream.headers.isEmpty()) { "External playback cannot supply plugin HTTP headers." }
        return http.approvedStreamUrl(stream.url, stream.headers)
    }

    override suspend fun close() = withContext(NonCancellable) {
        mutex.withLock {
            val wasOpen = stateLock.withLock { (!closed).also { closed = true } }
            if (wasOpen) {
                source.close()
            }
        }
    }

    private fun ensureOpen() = stateLock.withLock { if (closed) throw PluginSessionClosedException() }
}

private class BackgroundMediaSource(private val delegate: MediaSource, private val store: PluginStorageSession) : MediaSource by delegate {
    override suspend fun execute(operation: crow.evilo.plugin.api.PluginOperation): crow.evilo.plugin.api.PluginResult =
        withContext(Dispatchers.Default) { store.transaction { delegate.execute(operation) } }
    override suspend fun close() = withContext(NonCancellable + Dispatchers.Default) { store.dispose { delegate.close() } }
}
