package crow.evilo.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import crow.evilo.feature.browse.contentSelectorMorphTarget
import crow.evilo.model.MediaKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real path operations require Skia; Android host tests contain only android.graphics stubs. */
class EviloDockGeometryTest {
    private val progressValues = listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1f)

    @Test
    fun everyMorphOutlineStaysInsideTheReservedButtonBounds() {
        for (progress in progressValues) {
            for (size in listOf(Size(64f, 64f), Size(128f, 128f), Size(96f, 64f))) {
                val shape = EviloContentSelectorShape { progress }
                for (direction in LayoutDirection.entries) {
                    val outline = shape.createOutline(size, direction, Density(1f)) as Outline.Generic
                    val bounds = outline.path.getBounds()
                    assertTrue(bounds.left >= -0.01f)
                    assertTrue(bounds.top >= -0.01f)
                    assertTrue(bounds.right <= size.width + 0.01f)
                    assertTrue(bounds.bottom <= size.height + 0.01f)
                }
            }
        }
    }

    @Test
    fun notchBackgroundStillPaintsTheFullBottomAndSides() {
        for (width in listOf(320f, 480f, 600f)) {
            for (progress in progressValues) {
                val bounds = barPath(width, progress).getBounds()
                assertEquals(0f, bounds.left)
                assertEquals(24f, bounds.top)
                assertEquals(width, bounds.right)
                assertEquals(128f, bounds.bottom)
            }
        }
    }

    @Test
    fun everyMediaKindHasADistinctSelectionShape() {
        val targets = MediaKind.entries.map(::contentSelectorMorphTarget)
        assertEquals(MediaKind.entries.size, targets.toSet().size)
        assertTrue(targets.all { it in 0f..1f })
    }

    @Test
    fun notchChangesWithTheButtonInsteadOfKeepingACircularCutout() {
        val difference = Path.combine(PathOperation.Xor, barPath(320f, 0f), barPath(320f, 1f))
        assertFalse(difference.isEmpty)
    }

    @Test
    fun notchKeepsClearanceAroundEveryAnimatedOutline() {
        for (width in listOf(320f, 600f)) {
            for (progress in progressValues) {
                val bar = barPath(width, progress)
                val outline = EviloContentSelectorShape { progress }
                    .createOutline(Size(64f, 64f), LayoutDirection.Ltr, Density(1f)) as Outline.Generic
                // Translate the entire filled button to sample the 8px clearance disk,
                // not only its bounding rectangle or the contour's vertices.
                for (distance in listOf(0f, 4f, 8f)) {
                    for (index in 0 until 32) {
                        val angle = index * 2f * PI.toFloat() / 32f
                        val button = Path().apply {
                            addPath(outline.path, Offset(width / 2f - 32f + distance * cos(angle), distance * sin(angle)))
                        }
                        val overlap = Path.combine(PathOperation.Intersect, bar, button)
                        assertTrue(overlap.isEmpty, "Dock paints inside clearance: progress=$progress, distance=$distance, angle=$index")
                    }
                }
            }
        }
    }

    private fun barPath(width: Float, progress: Float): Path = EviloDockGeometry.barPath(
        Size(width, 128f), width / 2f, barTop = 24f, fabRadius = 32f,
        clearance = 8f, shoulderRadius = 12f, progress = progress,
    )
}
