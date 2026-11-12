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
    val Browse = glyph("Browse", "M12,3a9,9 0,1 0,0 18a9,9 0,1 0,0 -18z M15.2,8.5q.8,-.5 .3,.3l-2.3,3.9q-.2,.3 -.5,.5l-3.9,2.3q-.8,.5 -.3,-.3l2.3,-3.9q.2,-.3 .5,-.5z")
    val More = glyph("More", "M5,11a1,1 0,1 0,0 2a1,1 0,1 0,0 -2z M12,11a1,1 0,1 0,0 2a1,1 0,1 0,0 -2z M19,11a1,1 0,1 0,0 2a1,1 0,1 0,0 -2z")
    val Add = glyph("Add", "M12,5v14 M5,12h14")
    val Refresh = glyph("Refresh", "M20,7v4a1,1 0,0 1,-1 1h-4 M20,12a8,8 0,1 0,-2 5")
    val Down = glyph("Down", "M7,9.5L11.3,13.8Q12,14.5 12.7,13.8L17,9.5")
    val ChevronRight = glyph("ChevronRight", "M8.5,6L13.8,11.3Q14.5,12 13.8,12.7L8.5,18")
    val Back = glyph("Back", "M10,5.5L4.2,11.3Q3.5,12 4.2,12.7L10,18.5 M4,12H20")
    val Check = glyph("Check", "M5,12L8.3,15.3Q9,16 9.7,15.3L19,6")
    val Close = glyph("Close", "M6,6l12,12 M18,6L6,18")
    val Search = glyph("Search", "M10.5,3.5a7,7 0,1 0,0 14a7,7 0,1 0,0 -14z M16,16l5,5")
    val Link = glyph("Link", "M10,13a5,5 0,0 0,7 0l3,-3a5,5 0,0 0,-7 -7l-1.7,1.7 M14,11a5,5 0,0 0,-7 0l-3,3a5,5 0,0 0,7 7l1.7,-1.7")
    val Tune = glyph("Tune", "M4,6h8 M16,6h4 M4,12h2 M10,12h10 M4,18h10 M18,18h2 M14,4v4 M8,10v4 M16,16v4")
    val Settings = glyph("Settings", "M10.1,2.5H13.9Q14.4,2.5 14.5,3L14.9,5.2Q15,5.5 15.3,5.7L16.9,6.6Q17.2,6.8 17.5,6.7L19.6,6Q20.1,5.8 20.4,6.3L22,9.1Q22.3,9.6 21.9,9.9L20.2,11.4Q20,11.6 20,12Q20,12.4 20.2,12.6L21.9,14.1Q22.3,14.4 22,14.9L20.4,17.7Q20.1,18.2 19.6,18L17.5,17.3Q17.2,17.2 16.9,17.4L15.3,18.3Q15,18.5 14.9,18.8L14.5,21Q14.4,21.5 13.9,21.5H10.1Q9.6,21.5 9.5,21L9.1,18.8Q9,18.5 8.7,18.3L7.1,17.4Q6.8,17.2 6.5,17.3L4.4,18Q3.9,18.2 3.6,17.7L2,14.9Q1.7,14.4 2.1,14.1L3.8,12.6Q4,12.4 4,12Q4,11.6 3.8,11.4L2.1,9.9Q1.7,9.6 2,9.1L3.6,6.3Q3.9,5.8 4.4,6L6.5,6.7Q6.8,6.8 7.1,6.6L8.7,5.7Q9,5.5 9.1,5.2L9.5,3Q9.6,2.5 10.1,2.5z M12,8.5a3.5,3.5 0,1 0,0 7a3.5,3.5 0,1 0,0 -7z")
    val Shield = glyph("Shield", "M11.2,3.6q.8,-.4 1.6,0l6.4,2.4q.8,.3 .8,1.2V12c0,4.2 -5.8,7.7 -7.2,8.5q-.8,.5 -1.6,0C9.8,19.7 4,16.2 4,12V7.2q0,-.9 .8,-1.2z M8,12l2.3,2.3q.7,.7 1.4,-.1L16,9")
    val Info = glyph("Info", "M12,3a9,9 0,1 0,0 18a9,9 0,1 0,0 -18z M12,11v6 M12,7v.01")
    val Image = glyph("Image", "M5,3h14a2,2 0,0 1,2 2v14a2,2 0,0 1,-2 2H5a2,2 0,0 1,-2 -2V5a2,2 0,0 1,2 -2z M3,16l4.3,-4.3q.7,-.7 1.4,0L12,15l2.3,-2.3q.7,-.7 1.4,0L21,18 M15,7v.01")
    val Palette = glyph("Palette", "M12,3a9,9 0,1 0,0 18h1a2,2 0,0 0,1 -4a2,2 0,0 1,1 -4h2a4,4 0,0 0,4 -4c0,-4 -5,-6 -9,-6z M7,9v.01 M10,6v.01 M15,6v.01 M6,13v.01")
    val Book = glyph("Manga", "M12,6c-2.5,-1.7 -5,-1.9 -7.9,-1.2q-1.1,.3 -1.1,1.4V18q0,1 1.1,.7c2.6,-.6 5.3,-.6 7.9,1.3c2.6,-1.9 5.3,-1.9 7.9,-1.3q1.1,.3 1.1,-.7V6.2q0,-1.1 -1.1,-1.4C17,4.1 14.5,4.3 12,6z M12,6v14 M6,9h3 M15,9h3")
    val Novel = glyph("Novel", "M6,3h10a2,2 0,0 1,2 2v16H6a2,2 0,0 1,-2 -2V5a2,2 0,0 1,2 -2z M4,17h14 M8,7h6 M8,11h6")
    val Anime = glyph("Anime", "M5,5h14a2,2 0,0 1,2 2v10a2,2 0,0 1,-2 2H5a2,2 0,0 1,-2 -2V7a2,2 0,0 1,2 -2z M10,8.7q0,-.7 .6,-.3l4.8,3.1q.7,.5 0,1l-4.8,3.1q-.6,.4 -.6,-.3z")
    val Film = glyph("Film", "M3,9h18v10a2,2 0,0 1,-2 2H5a2,2 0,0 1,-2 -2z M3,9L2.4,5.3q-.2,-1 .8,-1.2L19.1,1.6q1,-.2 1.2,.8l.6,3.1q.2,1 -.8,1.2z M7,3.5l2,4 M13,2.5l2,4 M10,12.7q0,-.7 .6,-.3l3.8,2.2q.7,.4 0,.8l-3.8,2.2q-.6,.4 -.6,-.3z")

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
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}
