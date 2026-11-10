package crow.evilo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import crow.evilo.app.resources.Res
import crow.evilo.app.resources.app_loading
import crow.evilo.app.resources.app_retry
import crow.evilo.plugin.catalog.PluginCatalogRepository
import crow.evilo.plugin.api.HOST_HTTP_CAPABILITY
import crow.evilo.plugin.api.HOST_MEDIA_CAPABILITY
import crow.evilo.plugin.api.HOST_STORAGE_CAPABILITY
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.network.ktor.BoundedHttpTransport
import crow.evilo.network.ktor.createEviloHttpClient
import crow.evilo.storage.MemorySnapshotPersistence
import crow.evilo.storage.PersistentKeyValueStore
import crow.evilo.storage.SnapshotPersistence
import crow.evilo.feature.browse.BundledOfficialCatalogJson
import crow.evilo.feature.browse.HomeDestination
import crow.evilo.feature.browse.PluginHome
import crow.evilo.feature.reader.MediaBrowserRoute
import crow.evilo.ui.EviloTheme
import crow.wasmline.loader.WasmlineTrustedKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private class AppEnvironment(val repository: PluginCatalogRepository, val controller: AppPluginController,
    val icons: CatalogIconLoader, val transport: BoundedHttpTransport)

@Composable
fun App(persistenceOverride: SnapshotPersistence? = null) {
    val inspection = LocalInspectionMode.current
    val previewPersistence = remember { MemorySnapshotPersistence() }
    val persistence = persistenceOverride ?: if (inspection) previewPersistence else rememberAppPersistence()
    val scope = rememberCoroutineScope()
    var environment by remember { mutableStateOf<AppEnvironment?>(null) }
    var startupError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(persistence, attempt) {
        startupError = null
        var owned: AppEnvironment? = null
        try {
            val store = withContext(Dispatchers.Default) { PersistentKeyValueStore(persistence) }
            val host = HostDescriptor(GeneratedEviloConfiguration.APP_VERSION, GeneratedEviloConfiguration.APP_VERSION_CODE,
                capabilities = setOf(HOST_MEDIA_CAPABILITY, HOST_HTTP_CAPABILITY, HOST_STORAGE_CAPABILITY))
            val trustedKeys = WasmlineTrustedKeys { algorithm, keyId ->
                if (algorithm == "Ed25519" && keyId == GeneratedEviloConfiguration.PLUGIN_KEY_ID)
                    GeneratedEviloConfiguration.PLUGIN_PUBLIC_KEY_HEX.hexToByteArray() else null
            }
            val transport = BoundedHttpTransport(createEviloHttpClient())
            val repository = PluginCatalogRepository(store, transport, host)
            val controller = AppPluginController(repository, PluginSessionFactory(host, store, trustedKeys), scope)
            val ready = AppEnvironment(repository, controller, CatalogIconLoader(transport), transport)
            owned = ready
            withContext(Dispatchers.Default) { repository.initialize(BundledOfficialCatalogJson) }
            currentCoroutineContext().ensureActive()
            environment = ready
            if (!inspection) withContext(Dispatchers.Default) { repository.refresh() }
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            startupError = (failure.message ?: failure.toString()).take(4000)
        } finally {
            try {
                withContext(NonCancellable) { owned?.controller?.close() }
            } finally {
                owned?.transport?.close()
                if (environment === owned) environment = null
            }
        }
    }
    EviloTheme {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.background)))) {
            val ready = environment
            if (ready != null) ReadyApp(ready)
            else Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                if (startupError == null) {
                    CircularProgressIndicator()
                    Text(stringResource(Res.string.app_loading))
                } else {
                    Text(startupError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { attempt++ }) { Text(stringResource(Res.string.app_retry)) }
                }
            }
        }
    }
}

@Composable
private fun ReadyApp(environment: AppEnvironment) {
    val repositoryState by environment.repository.state.collectAsState()
    val connection by environment.controller.state.collectAsState()
    var destination by remember { mutableStateOf(HomeDestination.BROWSE) }
    val active = connection.active
    LaunchedEffect(active, repositoryState) {
        if (active != null) {
            val installed = repositoryState.installed.firstOrNull { it.key == active.installationKey }
            val reason = if (installed == null) "This plugin was uninstalled." else environment.repository.installationBlockReason(installed)
            if (installed == null || reason != null || repositoryState.selectedKind !in active.session.source.descriptor.kinds) {
                environment.controller.invalidate(reason ?: "The content type changed.")
            } else destination = HomeDestination.BROWSE
        }
    }
    PluginHome(environment.repository, environment.icons, destination,
        onDestination = { destination = it; environment.controller.closeActive() },
        onMediaChanged = environment.controller::closeActive, onInstall = environment.controller::install,
        onOpen = environment.controller::open, busy = connection.busy, error = connection.error,
        onCancel = environment.controller::closeActive, onDismissError = environment.controller::dismissError,
        contentOverride = if (active == null) null else {
            { MediaBrowserRoute(active.session, repositoryState.selectedKind, environment.controller::closeActive) }
        })
}
