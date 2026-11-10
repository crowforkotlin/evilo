package crow.evilo.feature.browse

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import crow.evilo.model.MediaKind
import crow.evilo.app.resources.Res
import crow.evilo.app.resources.kind_anime
import crow.evilo.app.resources.kind_film
import crow.evilo.app.resources.kind_manga
import crow.evilo.app.resources.kind_novel
import org.jetbrains.compose.resources.stringResource

enum class HomeDestination { BROWSE, MORE }

fun interface PluginIconLoader {
    suspend fun load(url: String, allowHttp: Boolean): ImageBitmap
}

@Composable
fun mediaKindLabel(kind: MediaKind): String = stringResource(when (kind) {
    MediaKind.MANGA -> Res.string.kind_manga
    MediaKind.NOVEL -> Res.string.kind_novel
    MediaKind.ANIME -> Res.string.kind_anime
    MediaKind.FILM -> Res.string.kind_film
})

internal object BrowseIcons {
    val Browse = glyph("Browse", "M3,3h7v7H3z M14,3h7v7h-7z M3,14h7v7H3z M14,14h7v7h-7z")
    val More = glyph("More", "M4,10h4v4H4z M10,10h4v4h-4z M16,10h4v4h-4z")
    val Add = glyph("Add", "M11,4h2v7h7v2h-7v7h-2v-7H4v-2h7z")
    val Refresh = glyph("Refresh", "M20,4v6h-6l2.3,-2.3A6,6 0,1 0,18 14h2A8,8 0,1 1,17.7 6.3z")
    val Down = glyph("Down", "M6,9l6,6l6,-6l-1.4,-1.4L12,12.2L7.4,7.6z")
    val Book = glyph("Book", "M3,4h8v16H3z M13,4h8v16h-8z")
    val Novel = glyph("Novel", "M5,3h14v18H5z M8,7v2h8V7z M8,11v2h8v-2z M8,15v2h5v-2z")
    val Anime = glyph("Anime", "M4,5h16v14H4z M10,8v8l6,-4z")
    val Film = glyph("Film", "M3,3h18v18H3z M6,6v3h3V6z M15,6v3h3V6z M6,15v3h3v-3z M15,15v3h3v-3z M10,8v8l5,-4z")
    fun kind(kind: MediaKind): ImageVector = when (kind) {
        MediaKind.MANGA -> Book
        MediaKind.NOVEL -> Novel
        MediaKind.ANIME -> Anime
        MediaKind.FILM -> Film
    }

    private fun glyph(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black)).build()
}
