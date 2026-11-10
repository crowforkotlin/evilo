package crow.evilo.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

import crow.evilo.app.resources.NotoSansSC
import crow.evilo.app.resources.Res

@OptIn(ExperimentalResourceApi::class)
@Composable
fun WithEviloFont(
    content: @Composable () -> Unit
) {
    val font by preloadFont(Res.font.NotoSansSC)

    var fontFallbackInitialized by remember { mutableStateOf(false) }
    val fontFamilyResolver = LocalFontFamilyResolver.current

    LaunchedEffect(fontFamilyResolver, font) {
        font?.let { font ->
            fontFamilyResolver.preload(FontFamily(font))
            fontFallbackInitialized = true
        }
    }

    if (fontFallbackInitialized) {
        content()
    }
}
