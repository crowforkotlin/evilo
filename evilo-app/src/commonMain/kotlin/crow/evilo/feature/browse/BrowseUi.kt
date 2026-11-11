package crow.evilo.feature.browse

import androidx.compose.runtime.Composable
import crow.evilo.model.MediaKind
import crow.evilo.app.resources.Res
import crow.evilo.app.resources.kind_anime
import crow.evilo.app.resources.kind_film
import crow.evilo.app.resources.kind_manga
import crow.evilo.app.resources.kind_novel
import org.jetbrains.compose.resources.stringResource

@Composable
fun mediaKindLabel(kind: MediaKind): String = stringResource(when (kind) {
    MediaKind.MANGA -> Res.string.kind_manga
    MediaKind.NOVEL -> Res.string.kind_novel
    MediaKind.ANIME -> Res.string.kind_anime
    MediaKind.FILM -> Res.string.kind_film
})

