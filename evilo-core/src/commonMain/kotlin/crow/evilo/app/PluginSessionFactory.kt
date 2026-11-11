package crow.evilo.app

import crow.evilo.model.MediaStream
import crow.evilo.plugin.catalog.CatalogPlugin
import crow.evilo.plugin.catalog.PluginRelease
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
import crow.wasmline.WasmlineConfig
import crow.wasmline.loader.WasmlineLoadOptions
import crow.wasmline.loader.WasmlineNoOpCache
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
) {
    suspend fun connect(plugin: CatalogPlugin, release: PluginRelease, allowHttp: Boolean): MediaSourceSession {
        val expected = PluginDescriptor(plugin.id, plugin.name, release.version, release.versionCode, release.kinds,
            release.languages, release.protocol, release.permissions)
        val packagePolicy = PackageNetworkPolicy(release.manifestUrl, allowHttp)
        val policy = PluginNetworkPolicy(plugin.id, release.permissions, allowHttp)
        val store = preferences.pluginSession(plugin.id)
        val http = PluginHttpClient(transport, policy)
        var loaded: MediaSource? = null
        try {
            withContext(Dispatchers.Default) {
                store.transaction { loaded = WasmlinePluginLoader(host, store, http).loadRemote(
                    packagePolicy.manifestUrl,
                    WasmlineLoadOptions(runtimeConfig = WasmlineConfig(supportConcurrent = true), trustedKeys = trustedKeys,
                        networkClient = PolicyPackageNetworkClient(transport, packagePolicy), cache = WasmlineNoOpCache,
                        manifestTtlMs = 0, maxCacheBytes = 128L * 1024 * 1024, maxArtifactBytes = 64L * 1024 * 1024),
                    expected, release.sourceRevision, release.sourceArchiveSha256,
                ) }
            }
            currentCoroutineContext().ensureActive()
            return PluginMediaSession(BackgroundMediaSource(checkNotNull(loaded), store), http)
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

    private fun ensureOpen() = stateLock.withLock { check(!closed) { "This plugin session is closed." } }
}

private class BackgroundMediaSource(private val delegate: MediaSource, private val store: PluginStorageSession) : MediaSource by delegate {
    override suspend fun execute(operation: crow.evilo.plugin.api.PluginOperation): crow.evilo.plugin.api.PluginResult =
        withContext(Dispatchers.Default) { store.transaction { delegate.execute(operation) } }
    override suspend fun close() = withContext(NonCancellable + Dispatchers.Default) { store.dispose { delegate.close() } }
}
