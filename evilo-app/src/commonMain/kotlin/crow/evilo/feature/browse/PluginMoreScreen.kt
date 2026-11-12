package crow.evilo.feature.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import crow.evilo.app.resources.*
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloSettingContent
import crow.evilo.ui.EviloSpacing
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PluginMoreScreen(state: PluginHomeState, dispatch: (PluginHomeIntent) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(EviloLayout.PagePadding),
        verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
        item { MoreEntry(onClick = { dispatch(PluginHomeIntent.ShowSettings()) }) {
            EviloSettingContent(EviloIcons.Settings, stringResource(Res.string.settings_title), stringResource(Res.string.settings_description)) {
                Icon(EviloIcons.ChevronRight, null)
            }
        } }
        item { MoreEntry(onClick = { dispatch(PluginHomeIntent.ShowSources) }) {
            EviloSettingContent(EviloIcons.Link, stringResource(Res.string.home_manage_sources),
                stringResource(Res.string.more_source_count, state.catalog.subscriptions.count { it.enabled })) { Icon(EviloIcons.ChevronRight, null) }
        } }
        item { MoreEntry(onClick = { dispatch(PluginHomeIntent.ShowSafety) }) {
            EviloSettingContent(EviloIcons.Shield, stringResource(Res.string.more_safety), stringResource(Res.string.more_safety_description)) {
                Icon(EviloIcons.ChevronRight, null)
            }
        } }
        item { MoreEntry(onClick = { dispatch(PluginHomeIntent.ShowAbout) }) {
            EviloSettingContent(EviloIcons.Info, stringResource(Res.string.settings_about), stringResource(Res.string.settings_license_description)) {
                Icon(EviloIcons.ChevronRight, null)
            }
        } }
    }
}

@Composable
private fun MoreEntry(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(EviloLayout.BorderWidth, MaterialTheme.colorScheme.outlineVariant), content = content)
}
