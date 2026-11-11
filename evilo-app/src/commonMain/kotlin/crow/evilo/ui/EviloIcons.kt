package crow.evilo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import crow.evilo.model.MediaKind

internal object EviloIcons {
    val Browse = glyph("Browse", "M12,3a9,9 0,1 0,0 18a9,9 0,1 0,0 -18z M16,8l-3,5l-5,3l3,-5z")
    val More = glyph("More", "M5,11a1,1 0,1 0,0 2a1,1 0,1 0,0 -2z M12,11a1,1 0,1 0,0 2a1,1 0,1 0,0 -2z M19,11a1,1 0,1 0,0 2a1,1 0,1 0,0 -2z")
    val Add = glyph("Add", "M12,5v14 M5,12h14")
    val Refresh = glyph("Refresh", "M20,7v5h-5 M20,12a8,8 0,1 0,-2 5")
    val Down = glyph("Down", "M7,10l5,5l5,-5")
    val ChevronRight = glyph("ChevronRight", "M9,6l6,6l-6,6")
    val Back = glyph("Back", "M15,5l-7,7l7,7 M8,12h12")
    val Check = glyph("Check", "M5,12l4,4l10,-10")
    val Close = glyph("Close", "M6,6l12,12 M18,6L6,18")
    val Search = glyph("Search", "M10.5,3.5a7,7 0,1 0,0 14a7,7 0,1 0,0 -14z M16,16l5,5")
    val Link = glyph("Link", "M10,13a5,5 0,0 0,7 0l3,-3a5,5 0,0 0,-7 -7l-1.7,1.7 M14,11a5,5 0,0 0,-7 0l-3,3a5,5 0,0 0,7 7l1.7,-1.7")
    val Tune = glyph("Tune", "M4,6h8 M16,6h4 M4,12h2 M10,12h10 M4,18h10 M18,18h2 M14,4v4 M8,10v4 M16,16v4")
    val Settings = glyph("Settings", "M9.5,2.5h5l.5,3l2,1.1l2.8,-1l2.5,4.3l-2.3,2v.2l2.3,2l-2.5,4.3l-2.8,-1l-2,1.1l-.5,3h-5l-.5,-3l-2,-1.1l-2.8,1L1.7,14.1l2.3,-2v-.2l-2.3,-2l2.5,-4.3l2.8,1l2,-1.1z M12,8.5a3.5,3.5 0,1 0,0 7a3.5,3.5 0,1 0,0 -7z")
    val Shield = glyph("Shield", "M12,3l8,3v6c0,5 -8,9 -8,9S4,17 4,12V6z M8,12l3,3l5,-6")
    val Info = glyph("Info", "M12,3a9,9 0,1 0,0 18a9,9 0,1 0,0 -18z M12,11v6 M12,7v.01")
    val Palette = glyph("Palette", "M12,3a9,9 0,1 0,0 18h1a2,2 0,0 0,1 -4a2,2 0,0 1,1 -4h2a4,4 0,0 0,4 -4c0,-4 -5,-6 -9,-6z M7,9v.01 M10,6v.01 M15,6v.01 M6,13v.01")
    val Book = glyph("Manga", "M12,6c-3,-2 -6,-2 -9,-1v14c3,-1 6,-1 9,1c3,-2 6,-2 9,-1V5c-3,-1 -6,-1 -9,1z M12,6v14 M6,9h3 M15,9h3")
    val Novel = glyph("Novel", "M6,3h10a2,2 0,0 1,2 2v16H6a2,2 0,0 1,-2 -2V5a2,2 0,0 1,2 -2z M4,17h14 M8,7h6 M8,11h6")
    val Anime = glyph("Anime", "M5,5h14a2,2 0,0 1,2 2v10a2,2 0,0 1,-2 2H5a2,2 0,0 1,-2 -2V7a2,2 0,0 1,2 -2z M10,8l6,4l-6,4z")
    val Film = glyph("Film", "M3,8h18v11a2,2 0,0 1,-2 2H5a2,2 0,0 1,-2 -2z M3,8L2,4l18,-3l1,4z M7,3l3,4 M13,2l3,4 M10,12l5,3l-5,3z")

    fun kind(kind: MediaKind): ImageVector = when (kind) {
        MediaKind.MANGA -> Book
        MediaKind.NOVEL -> Novel
        MediaKind.ANIME -> Anime
        MediaKind.FILM -> Film
    }

    private fun glyph(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            PathParser().parsePathString(path).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}
