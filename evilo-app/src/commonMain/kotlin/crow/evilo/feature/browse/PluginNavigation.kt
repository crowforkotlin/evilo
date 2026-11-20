package crow.evilo.feature.browse

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import crow.evilo.app.resources.*
import crow.evilo.model.MediaKind
import crow.evilo.plugin.catalog.PluginRepositoryState
import crow.evilo.ui.EviloContentSelectorShape
import crow.evilo.ui.EviloDockGeometry
import crow.evilo.ui.EviloElevation
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloMotion
import crow.evilo.ui.EviloSpacing
import crow.evilo.ui.LocalEviloPageActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CompactHomeNavigation(
    destination: HomeDestination,
    kind: MediaKind,
    onDestination: (HomeDestination) -> Unit,
    onKind: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val insets = NavigationBarDefaults.windowInsets
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val leftInset = insets.getLeft(density, layoutDirection)
    val rightInset = insets.getRight(density, layoutDirection)
    val selector = rememberContentSelectorMorph(kind)
    Box(Modifier.fillMaxWidth().drawWithCache {
        val centerX = (size.width + leftInset - rightInset) / 2f
        val path = EviloDockGeometry.barPath(
            size = size,
            centerX = centerX,
            barTop = EviloLayout.NavigationOverlap.toPx(),
            fabRadius = EviloLayout.ContentSelectorSize.toPx() / 2f,
            clearance = EviloLayout.NavigationFabClearance.toPx(),
            shoulderRadius = EviloLayout.NavigationShoulderRadius.toPx(),
            progress = selector.animation.value,
        )
        // Paint through the bottom/system insets, but leave the concavity transparent.
        // Do not clip the NavigationBar: its items keep their Material ripple and semantics.
        onDrawBehind { drawPath(path, colors.surfaceContainer) }
    }) {
        NavigationBar(
            Modifier.padding(top = EviloLayout.NavigationOverlap),
            containerColor = Color.Transparent,
            contentColor = colors.onSurface,
            tonalElevation = EviloElevation.Flat,
            windowInsets = insets,
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
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(insets),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ContentTypeButton(kind, onKind, selector.shape)
            Text(
                mediaKindLabel(kind),
                Modifier.width(EviloLayout.NavigationCenterSlot)
                    .padding(top = EviloLayout.NavigationLabelOffset - EviloLayout.ContentSelectorSize,
                        bottom = EviloSpacing.Small),
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
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
    val selector = rememberContentSelectorMorph(kind)
    NavigationRail(
        Modifier.fillMaxHeight().width(EviloLayout.RailWidth).verticalScroll(rememberScrollState()),
        containerColor = colors.surfaceContainerLow,
    ) {
        Text("evilo", Modifier.padding(top = EviloSpacing.ExtraLarge, bottom = EviloSpacing.Section),
            style = MaterialTheme.typography.titleMedium, color = colors.primary)
        ContentTypeButton(kind, onKind, selector.shape)
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
private fun ContentTypeButton(kind: MediaKind, onClick: () -> Unit, shape: Shape, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.home_select_kind)
    val currentKind = mediaKindLabel(kind)
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(EviloLayout.ContentSelectorSize).semantics {
            contentDescription = description
            stateDescription = currentKind
        },
        shape = shape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = EviloElevation.Floating),
    ) {
        // Icon crossfades stay transparent; page transitions paint an opaque surface.
        Crossfade(targetState = kind, modifier = Modifier.size(EviloLayout.IconSize),
            animationSpec = EviloMotion.componentSpec(), label = "content-type-icon") {
            Icon(EviloIcons.kind(it), null, Modifier.size(EviloLayout.IconSize))
        }
    }
}

internal fun contentSelectorMorphTarget(kind: MediaKind): Float = when (kind) {
    MediaKind.MANGA -> 0f
    MediaKind.NOVEL -> 1f / 3f
    MediaKind.ANIME -> 2f / 3f
    MediaKind.FILM -> 1f
}

private class ContentSelectorMorph(initial: Float) {
    val animation = Animatable(initial)
    val shape: Shape = EviloContentSelectorShape { animation.value }
}

@Composable
private fun rememberContentSelectorMorph(kind: MediaKind): ContentSelectorMorph {
    val target = contentSelectorMorphTarget(kind)
    val morph = remember { ContentSelectorMorph(target) }
    val pageActive = LocalEviloPageActive.current
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(target, pageActive, focused) {
        if (!pageActive) morph.animation.snapTo(target)
        else if (focused) morph.animation.animateTo(target, EviloMotion.spatialSpec())
        // A modal sheet owns focus while hiding. Keep the old outline until this
        // window regains focus, then animate rather than snapping under the sheet.
    }
    // The FAB outline and dock draw cache read this same value without recomposing
    // page data. There is no independent idle cycle delaying selection feedback.
    return morph
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContentTypeSheet(state: PluginRepositoryState, installedCounts: Map<MediaKind, Int>, enabled: Boolean,
    onDismiss: () -> Unit, onSelect: (MediaKind) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var pendingKind by remember { mutableStateOf<MediaKind?>(null) }
    val select by rememberUpdatedState(onSelect)
    val selectionEnabled by rememberUpdatedState(enabled)
    ModalBottomSheet(onDismissRequest = { if (pendingKind == null) onDismiss() }, containerColor = colors.surface,
        sheetState = sheetState, sheetMaxWidth = EviloLayout.SheetMaxWidth) {
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
                            onClick = {
                                if (selectionEnabled && pendingKind == null) {
                                    pendingKind = kind
                                    scope.launch {
                                        try {
                                            sheetState.hide()
                                            if (!sheetState.isVisible) {
                                                if (selectionEnabled) select(kind) else onDismiss()
                                            }
                                        } finally {
                                            pendingKind = null
                                        }
                                    }
                                }
                            },
                            enabled = enabled && pendingKind == null,
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
