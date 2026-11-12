package crow.evilo.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import crow.evilo.app.resources.*
import crow.evilo.feature.browse.PluginHomeIntent
import crow.evilo.feature.browse.PluginHomeState
import crow.evilo.feature.browse.SettingsSection
import crow.evilo.feature.browse.mediaKindLabel
import crow.evilo.feature.browse.SourceCheckStatus
import crow.evilo.feature.browse.sourceName
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloSettingContent
import crow.evilo.ui.EviloSettingsGroup
import crow.evilo.ui.EviloSpacing
import crow.evilo.ui.LocalEviloToastState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun settingsSectionTitle(section: SettingsSection): String = stringResource(when (section) {
    SettingsSection.ROOT -> Res.string.settings_title
    SettingsSection.BROWSE -> Res.string.settings_browse
    SettingsSection.PLUGINS -> Res.string.settings_plugins
    SettingsSection.IMAGES -> Res.string.settings_images
    SettingsSection.APPEARANCE -> Res.string.settings_appearance
    SettingsSection.SECURITY -> Res.string.settings_security
    SettingsSection.ABOUT -> Res.string.settings_about
})

@Composable
internal fun SettingsScreen(state: PluginHomeState, section: SettingsSection, host: HostDescriptor, dispatch: (PluginHomeIntent) -> Unit) {
    when (section) {
        SettingsSection.ROOT -> SettingsHome(dispatch)
        SettingsSection.SECURITY -> AppSafetyScreen()
        SettingsSection.ABOUT -> AppAboutScreen(host)
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(EviloLayout.PagePadding),
            verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
            when (section) {
                SettingsSection.BROWSE -> {
                    item { EviloSettingsGroup(stringResource(Res.string.settings_display_group)) {
                        SettingSwitch(EviloIcons.Browse, stringResource(Res.string.settings_merge_duplicates),
                            stringResource(Res.string.settings_merge_duplicates_description), state.catalog.mergeDuplicatePlugins,
                            state.canMutate) { dispatch(PluginHomeIntent.MergeDuplicatePlugins(it)) }
                        SettingsDivider()
                        EviloSettingContent(EviloIcons.Link, stringResource(Res.string.settings_source_grouping),
                            stringResource(Res.string.settings_source_grouping_description))
                    } }
                    item { EviloSettingsGroup(stringResource(Res.string.settings_content_group)) {
                        SettingSwitch(EviloIcons.Shield, stringResource(Res.string.more_show_nsfw), stringResource(Res.string.more_nsfw_description),
                            state.catalog.showNsfw, state.canMutate) { dispatch(PluginHomeIntent.ShowNsfw(it)) }
                        SettingsDivider()
                        SettingLink(EviloIcons.kind(state.catalog.selectedKind), stringResource(Res.string.home_select_kind),
                            mediaKindLabel(state.catalog.selectedKind)) { dispatch(PluginHomeIntent.ShowContentKinds) }
                    } }
                }
                SettingsSection.PLUGINS -> {
                    item { EviloSettingsGroup(stringResource(Res.string.settings_plugins)) {
                        SettingLink(EviloIcons.Link, stringResource(Res.string.home_manage_sources),
                            stringResource(Res.string.more_source_count, state.catalog.subscriptions.count { it.enabled })) { dispatch(PluginHomeIntent.ShowSources) }
                        SettingsDivider()
                        SettingLink(EviloIcons.Book, stringResource(Res.string.browse_manage_installed),
                            stringResource(Res.string.settings_installations_description, state.catalog.installed.size)) { dispatch(PluginHomeIntent.ShowInstalled) }
                    } }
                    item { EviloSettingsGroup(stringResource(Res.string.source_check_updates)) {
                        SettingLink(EviloIcons.Refresh, stringResource(Res.string.source_check_all), stringResource(Res.string.settings_updates_description),
                            enabled = !state.catalog.refreshing && state.catalog.subscriptions.any { it.enabled }) { dispatch(PluginHomeIntent.Refresh) }
                        state.catalog.subscriptions.filter { it.enabled }.forEach { source ->
                            Column(Modifier.padding(horizontal = EviloSpacing.Large, vertical = EviloSpacing.Small)) {
                                Text(sourceName(state, source.id), style = MaterialTheme.typography.titleSmall)
                                SourceCheckStatus(state, source.id, detailed = true)
                            }
                        }
                    } }
                }
                SettingsSection.IMAGES -> {
                    item { EviloSettingsGroup(stringResource(Res.string.settings_images)) {
                        EviloSettingContent(EviloIcons.Image, stringResource(Res.string.settings_image_formats), stringResource(Res.string.settings_image_formats_description))
                        SettingsDivider()
                        EviloSettingContent(EviloIcons.Shield, stringResource(Res.string.settings_image_limits), stringResource(Res.string.settings_image_limits_description))
                        SettingsDivider()
                        EviloSettingContent(EviloIcons.Refresh, stringResource(Res.string.settings_image_retry), stringResource(Res.string.settings_image_retry_description))
                        SettingsDivider()
                        EviloSettingContent(EviloIcons.Info, stringResource(Res.string.settings_image_details), stringResource(Res.string.settings_image_details_description))
                    } }
                }
                SettingsSection.APPEARANCE -> {
                    item { EviloSettingsGroup(stringResource(Res.string.settings_appearance)) {
                        EviloSettingContent(EviloIcons.Palette, stringResource(Res.string.more_system_theme), stringResource(Res.string.more_system_theme_description))
                        SettingsDivider()
                        EviloSettingContent(EviloIcons.Novel, stringResource(Res.string.more_font), stringResource(Res.string.more_font_description))
                    } }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun SettingsHome(dispatch: (PluginHomeIntent) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val padding = if (maxWidth >= EviloLayout.ExpandedBreakpoint) EviloLayout.WidePagePadding else EviloLayout.PagePadding
        val columns = if (maxWidth - padding * 2 >= EviloLayout.SettingsCardMinWidth * 2 + EviloSpacing.Large) 2 else 1
        LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize(), contentPadding = PaddingValues(padding),
            horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
            SettingsSection.entries.filter { it != SettingsSection.ROOT }.forEach { section -> item(key = section.name) {
                Surface(onClick = { dispatch(PluginHomeIntent.ShowSettings(section)) }, shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface, border = BorderStroke(EviloLayout.BorderWidth, MaterialTheme.colorScheme.outlineVariant)) {
                    EviloSettingContent(sectionIcon(section), settingsSectionTitle(section), sectionDescription(section)) {
                        Icon(EviloIcons.ChevronRight, null)
                    }
                }
            } }
        }
    }
}

@Composable
internal fun SettingLink(icon: ImageVector, title: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent) {
        EviloSettingContent(icon, title, description) { Icon(EviloIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SettingSwitch(icon: ImageVector, title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Surface(Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange), color = Color.Transparent) {
        EviloSettingContent(icon, title, description) { Switch(checked, onCheckedChange = null, enabled = enabled) }
    }
}

@Composable
private fun SettingsDivider() = HorizontalDivider(Modifier.padding(horizontal = EviloSpacing.Large), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
internal fun AppSafetyScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(EviloLayout.PagePadding), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
        item { EviloSettingsGroup(stringResource(Res.string.settings_security)) {
            EviloSettingContent(EviloIcons.Shield, stringResource(Res.string.settings_signature_authority), stringResource(Res.string.settings_signature_authority_description))
            SettingsDivider()
            EviloSettingContent(EviloIcons.Link, stringResource(Res.string.source_http_consent), stringResource(Res.string.settings_http_description))
            SettingsDivider()
            EviloSettingContent(EviloIcons.Book, stringResource(Res.string.settings_signed_loading), stringResource(Res.string.settings_signed_loading_description))
        } }
        item { Text(stringResource(Res.string.plugin_safety), style = MaterialTheme.typography.bodyMedium) }
        item { Text(stringResource(Res.string.more_reader_scope), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun AppAboutScreen(host: HostDescriptor) {
    val uri = LocalUriHandler.current
    val toasts = LocalEviloToastState.current
    val errorTitle = stringResource(Res.string.home_error)
    fun open(url: String) {
        try { uri.openUri(url) }
        catch (failure: Exception) { toasts.show((failure.message ?: failure.toString()).take(500), title = errorTitle, error = true) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(EviloLayout.PagePadding), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
        item { EviloSettingsGroup(stringResource(Res.string.settings_about)) {
            EviloSettingContent(EviloIcons.Info, stringResource(Res.string.app_name), stringResource(Res.string.more_about, host.appVersion, host.protocolMajor, host.protocolMinor))
            SettingsDivider()
            SettingLink(EviloIcons.Link, stringResource(Res.string.settings_project), "github.com/crowforkotlin/evilo") { open("https://github.com/crowforkotlin/evilo") }
        } }
        item { EviloSettingsGroup(stringResource(Res.string.settings_licenses)) {
            Column(Modifier.padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                Text(stringResource(Res.string.settings_license_description), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { open("https://github.com/crowforkotlin/evilo/blob/main/LICENSE") }) { Text(stringResource(Res.string.settings_licenses)) }
                OutlinedButton(onClick = { open("https://github.com/crowforkotlin/evilo/blob/main/evilo-app/src/commonMain/composeResources/files/ChillRound-OFL.txt") }) {
                    Text(stringResource(Res.string.settings_font_license))
                }
            }
        } }
    }
}

private fun sectionIcon(section: SettingsSection) = when (section) {
    SettingsSection.BROWSE -> EviloIcons.Browse
    SettingsSection.PLUGINS -> EviloIcons.Link
    SettingsSection.IMAGES -> EviloIcons.Image
    SettingsSection.APPEARANCE -> EviloIcons.Palette
    SettingsSection.SECURITY -> EviloIcons.Shield
    else -> EviloIcons.Info
}

@Composable
private fun sectionDescription(section: SettingsSection) = stringResource(when (section) {
    SettingsSection.BROWSE -> Res.string.settings_browse_description
    SettingsSection.PLUGINS -> Res.string.settings_plugins_description
    SettingsSection.IMAGES -> Res.string.settings_images_description
    SettingsSection.APPEARANCE -> Res.string.more_system_theme_description
    SettingsSection.SECURITY -> Res.string.more_safety_description
    else -> Res.string.settings_license_description
})
