package crow.evilo.feature.browse

import crow.evilo.app.PluginConnectionState
import crow.evilo.model.MediaKind
import crow.evilo.plugin.catalog.CatalogListing
import crow.evilo.plugin.catalog.InstalledPlugin
import crow.evilo.plugin.catalog.PluginRelease
import crow.evilo.plugin.catalog.PluginRepositoryState

enum class HomeDestination { BROWSE, MORE }
enum class CatalogFilter { ALL, INSTALLED, CERTIFIED }

data class PluginReleaseChoice(val release: PluginRelease, val blocked: String?)

data class PluginEntry(
    val key: String,
    val listing: CatalogListing?,
    val installation: InstalledPlugin?,
    val releases: List<PluginReleaseChoice>,
    val recommended: PluginRelease?,
    val openBlockReason: String?,
) {
    val plugin get() = listing?.plugin ?: checkNotNull(installation).plugin
    val allowHttp get() = listing?.snapshot?.subscription?.allowHttp ?: checkNotNull(installation).allowHttp
    val officiallyCertified get() = listing?.officiallyCertified == true
}

sealed interface HomeOverlay {
    data object ContentKinds : HomeOverlay
    data object Sources : HomeOverlay
    data class RemoveSource(val id: String) : HomeOverlay
    data object Safety : HomeOverlay
    data class PluginSettings(val key: String) : HomeOverlay
    data class PermissionReview(val key: String, val release: PluginRelease, val returnToSettings: Boolean) : HomeOverlay
    data class Uninstall(val key: String) : HomeOverlay
}

data class PluginHomeState(
    val catalog: PluginRepositoryState = PluginRepositoryState(),
    val connection: PluginConnectionState = PluginConnectionState(),
    val entries: Map<String, PluginEntry> = emptyMap(),
    val destination: HomeDestination = HomeDestination.BROWSE,
    val query: String = "",
    val filter: CatalogFilter = CatalogFilter.ALL,
    val overlay: HomeOverlay? = null,
    val changing: Boolean = false,
    val actionError: String? = null,
) {
    val busy get() = connection.busy || catalog.refreshing || changing
    val error get() = actionError ?: connection.error ?: catalog.error
}

sealed interface PluginHomeIntent {
    data class Navigate(val destination: HomeDestination) : PluginHomeIntent
    data class Search(val query: String) : PluginHomeIntent
    data class Filter(val filter: CatalogFilter) : PluginHomeIntent
    data object Refresh : PluginHomeIntent
    data object ShowSources : PluginHomeIntent
    data object ShowSafety : PluginHomeIntent
    data object ShowContentKinds : PluginHomeIntent
    data class SelectKind(val kind: MediaKind) : PluginHomeIntent
    data class ShowNsfw(val show: Boolean) : PluginHomeIntent
    data class AddSource(val url: String, val allowHttp: Boolean) : PluginHomeIntent
    data class EnableSource(val id: String, val enabled: Boolean) : PluginHomeIntent
    data class RequestSourceRemoval(val id: String) : PluginHomeIntent
    data class ConfirmSourceRemoval(val id: String) : PluginHomeIntent
    data class OpenPlugin(val key: String) : PluginHomeIntent
    data class ManagePlugin(val key: String) : PluginHomeIntent
    data class ReviewRelease(val key: String, val versionCode: Long) : PluginHomeIntent
    data class ApproveInstall(val review: HomeOverlay.PermissionReview) : PluginHomeIntent
    data class RequestUninstall(val key: String) : PluginHomeIntent
    data class ConfirmUninstall(val key: String) : PluginHomeIntent
    data object DismissOverlay : PluginHomeIntent
    data object ClosePlugin : PluginHomeIntent
    data object DismissError : PluginHomeIntent
}
