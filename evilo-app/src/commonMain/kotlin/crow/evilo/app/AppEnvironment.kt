package crow.evilo.app

import coil3.PlatformContext
import crow.evilo.architecture.closeResources
import crow.evilo.di.eviloCoreModule
import crow.evilo.di.eviloUiModule
import crow.evilo.feature.browse.BundledOfficialCatalogJson
import crow.evilo.feature.browse.PluginHomeStore
import crow.evilo.network.ktor.BoundedHttpTransport
import crow.evilo.plugin.api.HOST_HTTP_CAPABILITY
import crow.evilo.plugin.api.HOST_MEDIA_CAPABILITY
import crow.evilo.plugin.api.HOST_STORAGE_CAPABILITY
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.plugin.catalog.PluginCatalogRepository
import crow.wasmline.loader.WasmlineTrustedKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

internal class AppEnvironment private constructor(
    val koin: KoinApplication,
    val home: PluginHomeStore,
    private val connections: AppPluginController,
    private val images: EviloImages,
    private val transport: BoundedHttpTransport,
    private val storage: AppStorage,
    private val lifetime: Job,
) {
    private var closed = false

    suspend fun close() {
        if (closed) return
        closed = true
        closeResources(
            { home.close() },
            { connections.close() },
            { images.close() },
            { transport.close() },
            { lifetime.cancelAndJoin() },
            { storage.close() },
            { koin.close() },
        )
    }

    companion object {
        suspend fun create(factory: AppStorageFactory, context: PlatformContext, parentScope: CoroutineScope): AppEnvironment {
            val lifetime = SupervisorJob(parentScope.coroutineContext[Job])
            val scope = CoroutineScope(parentScope.coroutineContext + lifetime)
            var storage: AppStorage? = null
            var application: KoinApplication? = null
            var transport: BoundedHttpTransport? = null
            var images: EviloImages? = null
            var connections: AppPluginController? = null
            var home: PluginHomeStore? = null
            try {
                currentCoroutineContext().ensureActive()
                val backend = factory.create(scope).also { storage = it }
                val host = HostDescriptor(GeneratedEviloConfiguration.APP_VERSION, GeneratedEviloConfiguration.APP_VERSION_CODE,
                    capabilities = setOf(HOST_MEDIA_CAPABILITY, HOST_HTTP_CAPABILITY, HOST_STORAGE_CAPABILITY))
                val trustedKeys = WasmlineTrustedKeys { algorithm, keyId ->
                    if (algorithm == "Ed25519" && keyId == GeneratedEviloConfiguration.PLUGIN_KEY_ID)
                        GeneratedEviloConfiguration.PLUGIN_PUBLIC_KEY_HEX.hexToByteArray() else null
                }
                val container = koinApplication {
                    modules(eviloCoreModule(host, trustedKeys, backend.preferences, backend.catalogs, scope), eviloUiModule(context))
                }.also { application = it }
                val http = container.koin.get<BoundedHttpTransport>().also { transport = it }
                val imageLoader = container.koin.get<EviloImages>().also { images = it }
                val controller = container.koin.get<AppPluginController>().also { connections = it }
                val homeStore = container.koin.get<PluginHomeStore>().also { home = it }
                container.koin.get<PluginCatalogRepository>().initialize(BundledOfficialCatalogJson)
                currentCoroutineContext().ensureActive()
                return AppEnvironment(container, homeStore, controller, imageLoader, http, backend, lifetime)
            } catch (failure: Throwable) {
                try {
                    closeResources(
                        { home?.close() }, { connections?.close() }, { images?.close() }, { transport?.close() },
                        { lifetime.cancelAndJoin() }, { storage?.close() }, { application?.close() },
                    )
                } catch (closeFailure: Throwable) {
                    if (failure !== closeFailure) failure.addSuppressed(closeFailure)
                }
                throw failure
            }
        }
    }
}
