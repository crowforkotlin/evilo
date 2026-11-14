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
import crow.evilo.plugin.runtime.WasmlinePluginLoader
import crow.evilo.storage.EviloPreferencesRepository
import crow.evilo.storage.PluginStorageSession
import crow.evilo.storage.StoreLock
import crow.evilo.feature.reader.MediaSourceSession
import crow.evilo.feature.reader.PluginSessionClosedException
import crow.wasmline.WasmlineConfig
import crow.wasmline.loader.WasmlineLoadOptions
import crow.wasmline.loader.network.WasmlineHttpResponse
import crow.wasmline.loader.network.WasmlineHttpStatus
import crow.wasmline.loader.network.WasmlineNetworkClient
import crow.wasmline.loader.network.WasmlineNetworkSink
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
) : PluginSessionConnector {
    override suspend fun connect(plugin: CatalogPlugin, release: PluginRelease, allowHttp: Boolean,
        onPhase: (PluginLoadPhase) -> Unit): MediaSourceSession {
        return connectVerified(plugin, release, allowHttp, release.manifestSha256, onPhase)
    }

    override suspend fun connectInstalled(plugin: CatalogPlugin, release: PluginRelease, allowHttp: Boolean,
        approvedManifestSha256: String?, onPhase: (PluginLoadPhase) -> Unit): MediaSourceSession {
        require(approvedManifestSha256 != null) { "Review and reinstall this legacy installation to pin its signed manifest." }
        return connectVerified(plugin, release, allowHttp, approvedManifestSha256, onPhase)
    }

    private suspend fun connectVerified(plugin: CatalogPlugin, release: PluginRelease, allowHttp: Boolean,
        expectedManifestSha256: String?, onPhase: (PluginLoadPhase) -> Unit): MediaSourceSession {
        val callerContext = currentCoroutineContext()
        suspend fun report(phase: PluginLoadPhase) = withContext(callerContext) { onPhase(phase) }
        val expected = PluginDescriptor(plugin.id, plugin.name, release.version, release.versionCode, release.kinds,
            release.languages, release.protocol, release.permissions)
        val packagePolicy = PackageNetworkPolicy(release.manifestUrl, allowHttp)
        val policy = PluginNetworkPolicy(plugin.id, release.permissions, allowHttp)
        val store = preferences.pluginSession(plugin.id, publisherStorageKey(plugin.id, release, trustedKeys))
        val verificationKeys = releaseVerificationKeys(release, trustedKeys)
        var manifestDigest: String? = null
        val http = PluginHttpClient(transport, policy)
        var loaded: MediaSource? = null
        try {
            onPhase(PluginLoadPhase.LOADING_PACKAGE)
            withContext(Dispatchers.Default) {
                store.transaction { loaded = WasmlinePluginLoader(host, store, http).loadRemote(
                    packagePolicy.manifestUrl,
                    WasmlineLoadOptions(runtimeConfig = WasmlineConfig(supportConcurrent = true), trustedKeys = verificationKeys,
                        networkClient = PackageProgressClient(packagePolicy.manifestUrl,
                            PolicyPackageNetworkClient(transport, packagePolicy), ::report),
                        // A zero manifest TTL still fetches/verifies the signed manifest on every open.
                        // The default loader cache reuses only content-addressed, hash-checked artifacts.
                        manifestTtlMs = 0, maxCacheBytes = 128L * 1024 * 1024, maxArtifactBytes = 64L * 1024 * 1024),
                    expected, release.sourceRevision, release.sourceArchiveSha256,
                    onNegotiating = { report(PluginLoadPhase.NEGOTIATING) },
                    expectedManifestSha256 = expectedManifestSha256,
                    onManifestChecked = { manifestDigest = it },
                ) }
            }
            currentCoroutineContext().ensureActive()
            return PluginMediaSession(BackgroundMediaSource(checkNotNull(loaded), store), http, checkNotNull(manifestDigest))
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

/** Reports only observable boundaries; cache verification and runtime creation may share one phase. */
private class PackageProgressClient(
    private val manifestUrl: String,
    private val delegate: WasmlineNetworkClient,
    private val report: suspend (PluginLoadPhase) -> Unit,
) : WasmlineNetworkClient {
    override suspend fun fetch(url: String): WasmlineHttpResponse {
        report(if (url == manifestUrl) PluginLoadPhase.FETCHING_MANIFEST else PluginLoadPhase.DOWNLOADING_ARTIFACT)
        val response = delegate.fetch(url)
        if (response.statusCode in 200..299)
            report(if (url == manifestUrl) PluginLoadPhase.VERIFYING_PACKAGE else PluginLoadPhase.INITIALIZING_RUNTIME)
        return response
    }

    override suspend fun fetchTo(url: String, sink: WasmlineNetworkSink): WasmlineHttpStatus {
        report(if (url == manifestUrl) PluginLoadPhase.FETCHING_MANIFEST else PluginLoadPhase.DOWNLOADING_ARTIFACT)
        val response = delegate.fetchTo(url, sink)
        if (response.statusCode in 200..299)
            report(if (url == manifestUrl) PluginLoadPhase.VERIFYING_PACKAGE else PluginLoadPhase.INITIALIZING_RUNTIME)
        return response
    }
}

private class PluginMediaSession(
    override val source: MediaSource,
    private val http: PluginHttpClient,
    override val verifiedManifestSha256: String,
) : MediaSourceSession {
    private val mutex = Mutex()
    private val stateLock = StoreLock()
    private var closed = false

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
