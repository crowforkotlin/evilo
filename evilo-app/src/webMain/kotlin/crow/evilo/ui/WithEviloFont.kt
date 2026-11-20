package crow.evilo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import crow.evilo.app.resources.ChillRoundMSemiBold
import crow.evilo.app.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

@OptIn(ExperimentalResourceApi::class)
@Composable
fun WithEviloFont(content: @Composable (ready: Boolean) -> Unit) {
    val font by preloadFont(Res.font.ChillRoundMSemiBold, weight = FontWeight.SemiBold)
    val fontFamilyResolver = LocalFontFamilyResolver.current
    var fontFallbackInitialized by remember(fontFamilyResolver, font) { mutableStateOf(false) }

    LaunchedEffect(fontFamilyResolver, font) {
        font?.let {
            fontFamilyResolver.preload(FontFamily(it))
            fontFallbackInitialized = true
        }
    }

    // Storage and font loading share App's startup surface; neither needs a separate screen.
    content(fontFallbackInitialized)
}
