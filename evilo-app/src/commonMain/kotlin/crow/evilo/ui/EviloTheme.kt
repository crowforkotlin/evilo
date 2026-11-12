package crow.evilo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import crow.evilo.app.resources.ChillRoundMSemiBold
import crow.evilo.app.resources.Res
import org.jetbrains.compose.resources.Font

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A6B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB5F0EC),
    onPrimaryContainer = Color(0xFF003E3F),
    secondary = Color(0xFF4B6462),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E7E3),
    onSecondaryContainer = Color(0xFF233B37),
    tertiary = Color(0xFF795A30),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7DEB2),
    onTertiaryContainer = Color(0xFF48330F),
    background = Color(0xFFF7FAF9),
    onBackground = Color(0xFF192522),
    surface = Color.White,
    onSurface = Color(0xFF192522),
    surfaceVariant = Color(0xFFE2EAE7),
    onSurfaceVariant = Color(0xFF51625C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F5F3),
    surfaceContainer = Color(0xFFE9F0EE),
    surfaceContainerHigh = Color(0xFFE2EAE7),
    surfaceContainerHighest = Color(0xFFDCE4E1),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDCE4E1),
    surfaceTint = Color(0xFF006A6B),
    outline = Color(0xFF74847D),
    outlineVariant = Color(0xFFD6DFD9),
    inverseSurface = Color(0xFF293632),
    inverseOnSurface = Color(0xFFF0F5F3),
    inversePrimary = Color(0xFF78D6CF),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78D6CF),
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF00514E),
    onPrimaryContainer = Color(0xFFA4F0E9),
    secondary = Color(0xFFB4CDC4),
    onSecondary = Color(0xFF20352E),
    secondaryContainer = Color(0xFF374B43),
    onSecondaryContainer = Color(0xFFD6E7E3),
    tertiary = Color(0xFFE8C188),
    onTertiary = Color(0xFF432C0B),
    tertiaryContainer = Color(0xFF5D431F),
    onTertiaryContainer = Color(0xFFF7DEB2),
    background = Color(0xFF101716),
    onBackground = Color(0xFFE1ECE7),
    surface = Color(0xFF131B19),
    onSurface = Color(0xFFE1ECE7),
    surfaceVariant = Color(0xFF344A40),
    onSurfaceVariant = Color(0xFFB6C8BF),
    surfaceContainerLowest = Color(0xFF0C1210),
    surfaceContainerLow = Color(0xFF19231F),
    surfaceContainer = Color(0xFF1E2924),
    surfaceContainerHigh = Color(0xFF28342E),
    surfaceContainerHighest = Color(0xFF334039),
    surfaceBright = Color(0xFF334039),
    surfaceDim = Color(0xFF101716),
    surfaceTint = Color(0xFF78D6CF),
    outline = Color(0xFF7F9288),
    outlineVariant = Color(0xFF344A40),
    inverseSurface = Color(0xFFE1ECE7),
    inverseOnSurface = Color(0xFF293632),
    inversePrimary = Color(0xFF006A6B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EviloTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val font = Font(Res.font.ChillRoundMSemiBold, weight = FontWeight.SemiBold)
    val family = remember(font) { FontFamily(font) }
    val typography = remember(family) { eviloTypography(family) }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        motionScheme = EviloMotion.Scheme,
        typography = typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(EviloRadius.ExtraSmall),
            small = RoundedCornerShape(EviloRadius.Small),
            medium = RoundedCornerShape(EviloRadius.Medium),
            large = RoundedCornerShape(EviloRadius.Large),
            extraLarge = RoundedCornerShape(EviloRadius.ExtraLarge),
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun eviloTypography(family: FontFamily): Typography {
    // The supplied font has one face; hierarchy comes from size, not synthetic weights.
    fun style(size: Int, height: Int) = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSynthesis = FontSynthesis.None,
        fontSize = size.sp,
        lineHeight = height.sp,
        letterSpacing = 0.sp,
    )
    return Typography(
        displayLarge = style(40, 48),
        displayMedium = style(34, 42),
        displaySmall = style(30, 38),
        headlineLarge = style(28, 36),
        headlineMedium = style(24, 32),
        headlineSmall = style(22, 30),
        titleLarge = style(20, 28),
        titleMedium = style(16, 24),
        titleSmall = style(14, 20),
        bodyLarge = style(16, 26),
        bodyMedium = style(14, 22),
        bodySmall = style(12, 18),
        labelLarge = style(14, 20),
        labelMedium = style(12, 16),
        labelSmall = style(11, 16),
        displayLargeEmphasized = style(40, 48),
        displayMediumEmphasized = style(34, 42),
        displaySmallEmphasized = style(30, 38),
        headlineLargeEmphasized = style(28, 36),
        headlineMediumEmphasized = style(24, 32),
        headlineSmallEmphasized = style(22, 30),
        titleLargeEmphasized = style(20, 28),
        titleMediumEmphasized = style(16, 24),
        titleSmallEmphasized = style(14, 20),
        bodyLargeEmphasized = style(16, 26),
        bodyMediumEmphasized = style(14, 22),
        bodySmallEmphasized = style(12, 18),
        labelLargeEmphasized = style(14, 20),
        labelMediumEmphasized = style(12, 16),
        labelSmallEmphasized = style(11, 16),
    )
}
