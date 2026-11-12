package crow.evilo.di

import androidx.datastore.core.DataStore
import crow.evilo.app.AppPluginController
import crow.evilo.app.PluginSessionFactory
import crow.evilo.feature.browse.PluginHomeStore
import crow.evilo.feature.reader.MediaBrowserController
import crow.evilo.feature.reader.MediaSourceSession
import crow.evilo.model.MediaKind
import crow.evilo.network.ktor.BoundedHttpTransport
import crow.evilo.network.ktor.createEviloHttpClient
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.plugin.catalog.PluginCatalogRepository
import crow.evilo.storage.EviloPreferences
import crow.evilo.storage.EviloPreferencesRepository
import crow.evilo.storage.PluginCatalogStore
import crow.wasmline.loader.WasmlineTrustedKeys
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

fun eviloCoreModule(
    host: HostDescriptor,
    trustedKeys: WasmlineTrustedKeys,
    preferences: DataStore<EviloPreferences>,
    catalogs: PluginCatalogStore,
    appScope: CoroutineScope,
    debugDiagnostics: Boolean = false,
) = module {
    single { host }
    single { trustedKeys }
    single<DataStore<EviloPreferences>> { preferences }
    single<PluginCatalogStore> { catalogs }
    single { EviloPreferencesRepository(get()) }
    single { createEviloHttpClient() }
    single { BoundedHttpTransport(get()) }
    single { PluginCatalogRepository(get(), get(), get(), get()) }
    single { PluginSessionFactory(get(), get(), get(), get()) }
    single { AppPluginController(get(), get<PluginSessionFactory>(), appScope, debugDiagnostics) }
    single { PluginHomeStore(get(), get(), appScope) }
    factory { (session: MediaSourceSession, kind: MediaKind) -> MediaBrowserController(session, kind, appScope) }
}
