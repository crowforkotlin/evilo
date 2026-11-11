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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.compose.LocalPlatformContext
import crow.evilo.app.resources.Res
import crow.evilo.app.resources.app_loading
import crow.evilo.app.resources.app_retry
import crow.evilo.feature.browse.PluginHome
import crow.evilo.feature.browse.PluginHomeIntent
import crow.evilo.ui.EviloSpacing
import crow.evilo.ui.EviloTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinIsolatedContext

@Composable
fun App() {
    val inspection = LocalInspectionMode.current
    val factory = if (inspection) remember { AppStorageFactory { AppStorage.preview() } } else rememberAppStorageFactory()
    val context = LocalPlatformContext.current
    val environments = remember { Mutex() }
    var environment by remember { mutableStateOf<AppEnvironment?>(null) }
    var startupError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(factory, context, attempt) {
        // A retry cannot open the same DataStore files until the previous environment has closed.
        environments.withLock {
            startupError = null
            var owned: AppEnvironment? = null
            var failure: Throwable? = null
            try {
                val ready = AppEnvironment.create(factory, context, CoroutineScope(currentCoroutineContext()))
                owned = ready
                environment = ready
                if (!inspection) ready.home.dispatch(PluginHomeIntent.Refresh)
                awaitCancellation()
            } catch (error: Throwable) {
                failure = error
                if (error is CancellationException || error !is Exception) throw error
                startupError = (error.message ?: error.toString()).take(4000)
            } finally {
                if (environment === owned) environment = null
                try {
                    withContext(NonCancellable) { owned?.close() }
                } catch (closeFailure: Throwable) {
                    val original = failure
                    if (original != null) {
                        if (original !== closeFailure) original.addSuppressed(closeFailure)
                    } else startupError = (closeFailure.message ?: closeFailure.toString()).take(4000)
                }
            }
        }
    }

    EviloTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.background)))) {
                val ready = environment
                if (ready != null) KoinIsolatedContext(ready.koin) { PluginHome() }
                else Column(Modifier.fillMaxSize().padding(EviloSpacing.ExtraLarge), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large, Alignment.CenterVertically)) {
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
}
