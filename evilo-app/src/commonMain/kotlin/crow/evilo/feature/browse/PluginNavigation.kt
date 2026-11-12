package crow.evilo.feature.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import crow.evilo.app.resources.*
import crow.evilo.model.MediaKind
import crow.evilo.plugin.catalog.PluginRepositoryState
import crow.evilo.ui.EviloElevation
import crow.evilo.ui.EviloContentTransition
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloMotion
import crow.evilo.ui.EviloSpacing
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CompactHomeNavigation(
    destination: HomeDestination,
    kind: MediaKind,
    onDestination: (HomeDestination) -> Unit,
    onKind: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth()) {
        NavigationBar(
            Modifier.padding(top = EviloLayout.NavigationOverlap),
            containerColor = colors.surfaceContainer,
            tonalElevation = EviloElevation.Flat,
        ) {
            HomeDestination.entries.forEachIndexed { index, tab ->
                if (index == 1) Spacer(Modifier.width(EviloLayout.NavigationCenterSlot))
                NavigationBarItem(
                    selected = destination == tab,
                    onClick = { if (destination != tab) onDestination(tab) },
                    icon = { Icon(destinationIcon(tab), null) },
                    label = { Text(destinationLabel(tab), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        indicatorColor = colors.primaryContainer,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant,
                    ),
                )
            }
        }
        ContentTypeButton(kind, onKind, Modifier.align(Alignment.TopCenter))
        Text(
            mediaKindLabel(kind),
            Modifier.align(Alignment.TopCenter).width(EviloLayout.NavigationCenterSlot)
                .padding(top = EviloLayout.NavigationLabelOffset),
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun ExpandedHomeNavigation(
    destination: HomeDestination,
    kind: MediaKind,
    onDestination: (HomeDestination) -> Unit,
    onKind: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    NavigationRail(
        Modifier.fillMaxHeight().width(EviloLayout.RailWidth).verticalScroll(rememberScrollState()),
        containerColor = colors.surfaceContainerLow,
    ) {
        Text("evilo", Modifier.padding(top = EviloSpacing.ExtraLarge, bottom = EviloSpacing.Section),
            style = MaterialTheme.typography.titleMedium, color = colors.primary)
        ContentTypeButton(kind, onKind)
        Text(mediaKindLabel(kind), Modifier.padding(top = EviloSpacing.Small),
            style = MaterialTheme.typography.labelMedium, color = colors.primary)
        Spacer(Modifier.height(EviloSpacing.Section))
        HomeDestination.entries.forEach { tab ->
            NavigationRailItem(
                selected = destination == tab,
                onClick = { if (destination != tab) onDestination(tab) },
                icon = { Icon(destinationIcon(tab), null) },
                label = { Text(destinationLabel(tab), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = colors.primary,
                    selectedTextColor = colors.primary,
                    indicatorColor = colors.primaryContainer,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
            )
            Spacer(Modifier.height(EviloSpacing.Small))
        }
    }
}

@Composable
private fun ContentTypeButton(kind: MediaKind, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.home_select_kind)
    val currentKind = mediaKindLabel(kind)
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(EviloLayout.ContentSelectorSize).semantics {
            contentDescription = description
            stateDescription = currentKind
        },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = EviloElevation.Floating),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EviloContentTransition(targetState = kind, label = "content-type-icon") {
                Icon(EviloIcons.kind(it), null, Modifier.size(EviloLayout.IconSize))
            }
            Icon(EviloIcons.Down, null, Modifier.size(EviloLayout.ContentSelectorChevronSize))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContentTypeSheet(state: PluginRepositoryState, installedCounts: Map<MediaKind, Int>, enabled: Boolean,
    onDismiss: () -> Unit, onSelect: (MediaKind) -> Unit) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), sheetMaxWidth = EviloLayout.SheetMaxWidth) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = EviloSpacing.ExtraLarge, end = EviloSpacing.ExtraLarge, bottom = EviloSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraLarge),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                Text(stringResource(Res.string.home_select_kind), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.home_kind_description), style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant)
            }
            MediaKind.entries.chunked(2).forEach { kinds ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                    kinds.forEach { kind ->
                        val active = state.selectedKind == kind
                        val count = installedCounts[kind] ?: 0
                        val container by animateColorAsState(if (active) colors.primaryContainer else colors.surfaceContainerLow,
                            EviloMotion.componentSpec(), label = "kind-container")
                        val contentColor by animateColorAsState(if (active) colors.onPrimaryContainer else colors.onSurface,
                            EviloMotion.componentSpec(), label = "kind-content")
                        val outline by animateColorAsState(if (active) colors.primary else colors.outlineVariant,
                            EviloMotion.componentSpec(), label = "kind-outline")
                        Surface(
                            onClick = { onSelect(kind) },
                            enabled = enabled,
                            modifier = Modifier.weight(1f).semantics { selected = active },
                            shape = MaterialTheme.shapes.large,
                            color = container,
                            contentColor = contentColor,
                            border = BorderStroke(EviloLayout.BorderWidth, outline),
                        ) {
                            Column(Modifier.heightIn(min = EviloLayout.ContentChoiceMinHeight).padding(EviloSpacing.Large),
                                verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Icon(EviloIcons.kind(kind), null, tint = colors.primary)
                                    if (active) Icon(EviloIcons.Check, stringResource(Res.string.home_kind_selected))
                                }
                                Text(mediaKindLabel(kind), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(Res.string.home_kind_plugin_count, count), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun destinationLabel(destination: HomeDestination): String = stringResource(
    if (destination == HomeDestination.BROWSE) Res.string.home_browse else Res.string.home_more,
)

private fun destinationIcon(destination: HomeDestination) =
    if (destination == HomeDestination.BROWSE) EviloIcons.Browse else EviloIcons.More
