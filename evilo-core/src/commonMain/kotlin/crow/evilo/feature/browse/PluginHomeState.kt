package crow.evilo.feature.browse

import crow.evilo.app.PluginConnectionState
import crow.evilo.model.MediaKind
import crow.evilo.plugin.catalog.CatalogListing
import crow.evilo.plugin.catalog.InstalledPlugin
import crow.evilo.plugin.catalog.PluginRelease
import crow.evilo.plugin.catalog.PluginRepositoryState
import crow.evilo.plugin.catalog.PluginBlock

enum class HomeDestination { BROWSE, MORE }
enum class CatalogFilter { ALL, INSTALLED }

data class PluginReleaseChoice(val release: PluginRelease, val blocked: String?, val block: PluginBlock? = null)

data class PluginEntry(
    val key: String,
    val listing: CatalogListing?,
    val installation: InstalledPlugin?,
    val releases: List<PluginReleaseChoice>,
    val recommended: PluginRelease?,
    val openBlockReason: String?,
    val openBlock: PluginBlock? = null,
) {
    val plugin get() = listing?.plugin ?: checkNotNull(installation).plugin
    val allowHttp get() = listing?.snapshot?.subscription?.allowHttp ?: checkNotNull(installation).allowHttp
    val sourceId get() = listing?.snapshot?.subscription?.id ?: checkNotNull(installation).sourceId
}

enum class SettingsSection { ROOT, BROWSE, PLUGINS, IMAGES, APPEARANCE, SECURITY, ABOUT }

sealed interface HomePage {
    data object Sources : HomePage
    data object AddSource : HomePage
    data class SourceCatalog(val id: String) : HomePage
    data class SourceSettings(val id: String) : HomePage
    data class PluginDetail(val key: String, val rememberSourceOnOpen: Boolean = true) : HomePage
    data class ChooseSource(val pluginId: String) : HomePage
    data class PluginBrowser(val key: String, val rememberSourceOnOpen: Boolean = true) : HomePage
    data object InstalledPlugins : HomePage
    data class Settings(val section: SettingsSection = SettingsSection.ROOT) : HomePage
    data object Safety : HomePage
    data object About : HomePage
}

sealed interface HomeOverlay {
    data object ContentKinds : HomeOverlay
    data object BrowseSources : HomeOverlay
    data class RemoveSource(val id: String) : HomeOverlay
    data class PermissionReview(val key: String, val release: PluginRelease, val openAfterInstall: Boolean = false) : HomeOverlay
    data class Uninstall(val key: String) : HomeOverlay
}

data class HomeNotice(val installationKey: String)

data class PluginHomeState(
    val catalog: PluginRepositoryState = PluginRepositoryState(),
    val connection: PluginConnectionState = PluginConnectionState(),
    val entries: Map<String, PluginEntry> = emptyMap(),
    val destination: HomeDestination = HomeDestination.BROWSE,
    val query: String = "",
    val browseSourceId: String? = null,
    val sourceQueries: Map<String, String> = emptyMap(),
    val sourceFilters: Map<String, CatalogFilter> = emptyMap(),
    val installedQuery: String = "",
    val pages: List<HomePage> = emptyList(),
    val browse: BrowseProjection = BrowseProjection(),
    val kindCounts: Map<MediaKind, Int> = emptyMap(),
    val searchLabels: BrowseSearchLabels = BrowseSearchLabels(),
    val overlay: HomeOverlay? = null,
    val changing: Boolean = false,
    val actionError: String? = null,
    val notice: HomeNotice? = null,
) {
    val busy get() = connection.busy || catalog.refreshing || changing
    val error get() = actionError ?: connection.error ?: catalog.error
    val page get() = pages.lastOrNull()
    val canMutate get() = !connection.busy && !changing
}

sealed interface PluginHomeIntent {
    data class Navigate(val destination: HomeDestination) : PluginHomeIntent
    data class Search(val query: String) : PluginHomeIntent
    data class SetSearchLabels(val labels: BrowseSearchLabels) : PluginHomeIntent
    data object Back : PluginHomeIntent
    data object ShowBrowseSources : PluginHomeIntent
    data class SelectBrowseSource(val id: String?) : PluginHomeIntent
    data class SearchSource(val id: String, val query: String) : PluginHomeIntent
    data class FilterSource(val id: String, val filter: CatalogFilter) : PluginHomeIntent
    data class SearchInstalled(val query: String) : PluginHomeIntent
    data object Refresh : PluginHomeIntent
    data class RefreshSource(val id: String) : PluginHomeIntent
    data object ShowSources : PluginHomeIntent
    data object ShowSafety : PluginHomeIntent
    data object ShowAbout : PluginHomeIntent
    data object ShowAddSource : PluginHomeIntent
    data class ShowSourceCatalog(val id: String) : PluginHomeIntent
    data class ShowSourceSettings(val id: String) : PluginHomeIntent
    data class ShowSettings(val section: SettingsSection = SettingsSection.ROOT) : PluginHomeIntent
    data object ShowInstalled : PluginHomeIntent
    data object ShowContentKinds : PluginHomeIntent
    data class SelectKind(val kind: MediaKind) : PluginHomeIntent
    data class ShowNsfw(val show: Boolean) : PluginHomeIntent
    data class MergeDuplicatePlugins(val merge: Boolean) : PluginHomeIntent
    data class AddSource(val url: String, val allowHttp: Boolean) : PluginHomeIntent
    data class EnableSource(val id: String, val enabled: Boolean) : PluginHomeIntent
    data class RequestSourceRemoval(val id: String) : PluginHomeIntent
    data class ConfirmSourceRemoval(val id: String) : PluginHomeIntent
    data class OpenPlugin(val key: String, val rememberSource: Boolean = true) : PluginHomeIntent
    data class ChoosePluginSource(val pluginId: String) : PluginHomeIntent
    data class SelectPreferredInstallation(val key: String) : PluginHomeIntent
    data class ManagePlugin(val key: String, val rememberSourceOnOpen: Boolean = true) : PluginHomeIntent
    data class ReviewRelease(val key: String, val versionCode: Long, val openAfterInstall: Boolean = false) : PluginHomeIntent
    data class ApproveInstall(val review: HomeOverlay.PermissionReview) : PluginHomeIntent
    data class RequestUninstall(val key: String) : PluginHomeIntent
    data class ConfirmUninstall(val key: String) : PluginHomeIntent
    data object DismissOverlay : PluginHomeIntent
    data object ClosePlugin : PluginHomeIntent
    data object DismissError : PluginHomeIntent
    data class DismissTransientError(val expectedActionError: String? = null, val expectedConnectionError: String? = null) : PluginHomeIntent
    data object DismissNotice : PluginHomeIntent
}
