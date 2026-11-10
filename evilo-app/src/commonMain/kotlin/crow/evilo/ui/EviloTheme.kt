package crow.evilo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import crow.evilo.app.resources.NotoSansSC
import crow.evilo.app.resources.Res
import org.jetbrains.compose.resources.Font

private val LightColors = lightColorScheme(
    primary = Color(0xFF285B48), onPrimary = Color.White, primaryContainer = Color(0xFFC7EAD7), onPrimaryContainer = Color(0xFF0D3625),
    secondary = Color(0xFF755B41), secondaryContainer = Color(0xFFF0DCC5), onSecondaryContainer = Color(0xFF2C1807),
    tertiary = Color(0xFF406777), tertiaryContainer = Color(0xFFC2E8F8),
    background = Color(0xFFF6F8F2), onBackground = Color(0xFF1A211C), surface = Color(0xFFF8FAF5), onSurface = Color(0xFF1A211C),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F4EC), surfaceContainer = Color(0xFFEAF0E6),
    surfaceContainerHigh = Color(0xFFE4EADF), surfaceContainerHighest = Color(0xFFDCE4D7),
    onSurfaceVariant = Color(0xFF465147), outline = Color(0xFF768476), outlineVariant = Color(0xFFC6D0C1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ED4B7), onPrimary = Color(0xFF003824), primaryContainer = Color(0xFF154D39), onPrimaryContainer = Color(0xFFC7EAD7),
    secondary = Color(0xFFDEC1A3), secondaryContainer = Color(0xFF55412C), onSecondaryContainer = Color(0xFFF0DCC5),
    tertiary = Color(0xFF9ECCE0), background = Color(0xFF111813), onBackground = Color(0xFFE1E8DD),
    surface = Color(0xFF111813), onSurface = Color(0xFFE1E8DD), surfaceContainerLowest = Color(0xFF0C120E),
    surfaceContainerLow = Color(0xFF19211A), surfaceContainer = Color(0xFF1D261F), surfaceContainerHigh = Color(0xFF283229),
    surfaceContainerHighest = Color(0xFF333D33), onSurfaceVariant = Color(0xFFC2CDBE), outlineVariant = Color(0xFF465246),
)

@Composable
fun EviloTheme(content: @Composable () -> Unit) {
    val body = FontFamily(Font(Res.font.NotoSansSC))
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = FontFamily.Serif), displayMedium = base.displayMedium.copy(fontFamily = FontFamily.Serif),
        displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif), headlineLarge = base.headlineLarge.copy(fontFamily = body),
        headlineMedium = base.headlineMedium.copy(fontFamily = body), headlineSmall = base.headlineSmall.copy(fontFamily = body),
        titleLarge = base.titleLarge.copy(fontFamily = body), titleMedium = base.titleMedium.copy(fontFamily = body), titleSmall = base.titleSmall.copy(fontFamily = body),
        bodyLarge = base.bodyLarge.copy(fontFamily = body), bodyMedium = base.bodyMedium.copy(fontFamily = body), bodySmall = base.bodySmall.copy(fontFamily = body),
        labelLarge = base.labelLarge.copy(fontFamily = body), labelMedium = base.labelMedium.copy(fontFamily = body), labelSmall = base.labelSmall.copy(fontFamily = body),
    )
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, typography = typography,
        shapes = Shapes(RoundedCornerShape(6.dp), RoundedCornerShape(12.dp), RoundedCornerShape(20.dp), RoundedCornerShape(28.dp), RoundedCornerShape(36.dp)),
        content = content)
}
