package crow.evilo.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal object EviloDockGeometry {
    private const val OutlineSamples = 128
    private const val NotchSamples = 160

    fun barPath(
        size: Size,
        centerX: Float,
        barTop: Float,
        fabRadius: Float,
        clearance: Float,
        shoulderRadius: Float,
        progress: Float,
    ): Path {
        val morph = progress.coerceIn(0f, 1f)
        val outline = List(OutlineSamples) { index ->
            val point = selectorPoint(index * 2f * PI.toFloat() / OutlineSamples, fabRadius, morph)
            Offset(centerX + point[0], fabRadius + point[1])
        }
        // The radial curve's speed is bounded by 1.25 * radius. Expand by half a
        // sampling interval (plus Bezier approximation slack) to preserve clearance
        // between samples, not only at their vertices.
        val sampleSlack = fabRadius * (1.25f * PI.toFloat() / OutlineSamples + 0.01f)
        val gap = clearance + sampleSlack
        val rounding = shoulderRadius.coerceAtLeast(0f)
        val expandedRadius = gap + rounding
        val extent = fabRadius + expandedRadius
        val left = centerX - extent
        val step = extent * 2f / NotchSamples

        fun expandedEdge(x: Float): Float {
            var edge = barTop + rounding
            for (point in outline) {
                // Widen by one horizontal sample so linear interpolation cannot
                // paint inside the clearance envelope between neighboring vertices.
                val dx = (abs(x - point.x) - step).coerceAtLeast(0f)
                if (dx <= expandedRadius) {
                    edge = max(edge, point.y + sqrt((expandedRadius * expandedRadius - dx * dx).coerceAtLeast(0f)))
                }
            }
            return edge
        }

        // Closing the upper half-plane plus the clearance envelope with a disk
        // rounds the shoulders without cutting into the gap: dilate by the shoulder
        // radius, then erode by the same radius. A sampled erosion is conservative.
        val shoulderSteps = if (rounding > 0f) max(1, (rounding / step).toInt()) else 0
        val expanded = FloatArray(NotchSamples + shoulderSteps * 2 + 1) { index ->
            expandedEdge(left + (index - shoulderSteps) * step)
        }
        val offsets = (-shoulderSteps..shoulderSteps).map { index ->
            val dx = index * step
            index to sqrt((rounding * rounding - dx * dx).coerceAtLeast(0f))
        }
        return Path().apply {
            moveTo(0f, barTop)
            lineTo(left - step, barTop)
            for (index in 0..NotchSamples) {
                var edge = Float.POSITIVE_INFINITY
                for ((offset, height) in offsets) {
                    edge = min(edge, expanded[index + shoulderSteps + offset] - height)
                }
                lineTo(left + index * step, max(barTop, edge))
            }
            lineTo(centerX + extent + step, barTop)
            lineTo(size.width, barTop)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
    }
}

private fun selectorPoint(angle: Float, radius: Float, morph: Float): FloatArray {
    val wave = (1f - morph) * cos(4f * angle) + morph * cos(6f * angle)
    val waveDerivative = -4f * (1f - morph) * sin(4f * angle) - 6f * morph * sin(6f * angle)
    val r = radius * (0.88f + 0.12f * wave)
    val derivative = radius * 0.12f * waveDerivative
    val c = cos(angle)
    val s = sin(angle)
    return floatArrayOf(r * c, r * s, derivative * c - r * s, derivative * s + r * c)
}

internal class EviloContentSelectorShape(private val progress: () -> Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val morph = progress().coerceIn(0f, 1f)
        val radius = min(size.width, size.height) / 2f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val step = 2f * PI.toFloat() / SegmentCount

        fun boundedControl(x: Float, y: Float): FloatArray {
            // The complete cubic stays inside the reserved disk, including controls.
            val distance = sqrt(x * x + y * y)
            val scale = if (distance > radius) radius / distance else 1f
            return floatArrayOf(centerX + x * scale, centerY + y * scale)
        }

        val path = Path()
        var previous = selectorPoint(-PI.toFloat() / 2f, radius, morph)
        path.moveTo(centerX + previous[0], centerY + previous[1])
        for (index in 1..SegmentCount) {
            val next = selectorPoint(-PI.toFloat() / 2f + index * step, radius, morph)
            val firstControl = boundedControl(previous[0] + previous[2] * step / 3f,
                previous[1] + previous[3] * step / 3f)
            val secondControl = boundedControl(next[0] - next[2] * step / 3f,
                next[1] - next[3] * step / 3f)
            path.cubicTo(firstControl[0], firstControl[1], secondControl[0], secondControl[1],
                centerX + next[0], centerY + next[1])
            previous = next
        }
        path.close()
        return Outline.Generic(path)
    }

    private companion object {
        const val SegmentCount = 48
    }
}
