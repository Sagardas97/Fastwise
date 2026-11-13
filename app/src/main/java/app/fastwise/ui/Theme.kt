package app.fastwise.ui

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fastwise.R

/** Colors the Material scheme doesn't cover: the fasting arc, meals, success and the 24h dial. */
@Immutable
data class FastColors(
    val arcStart: Color,
    val arcEnd: Color,
    val meal: Color,
    val success: Color,
    val night: Color,
    val track: Color,
)

// "Dusk" palette: fasting mostly happens overnight, so the app lives in night-sky ink with a dawn-amber arc.
private val DarkFast = FastColors(
    arcStart = Color(0xFFF4B860),
    arcEnd = Color(0xFFFFE2A8),
    meal = Color(0xFFE77B92),
    success = Color(0xFF8CC7A1),
    night = Color(0xFF3A4170),
    track = Color(0xFF2A2F4D),
)
private val BlackFast = DarkFast.copy(night = Color(0xFF2E3460), track = Color(0xFF1C1F31))
private val LightFast = FastColors(
    arcStart = Color(0xFFE39A2D),
    arcEnd = Color(0xFFB87412),
    meal = Color(0xFFC2456A),
    success = Color(0xFF3E9461),
    night = Color(0xFFB9BEDD),
    track = Color(0xFFDDE0EE),
)

val LocalFastColors = staticCompositionLocalOf { DarkFast }

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF4B860),
    onPrimary = Color(0xFF2B1A00),
    primaryContainer = Color(0xFF5A3E12),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFFC9CDE8),
    onSecondary = Color(0xFF1E2240),
    secondaryContainer = Color(0xFF2E3456),
    onSecondaryContainer = Color(0xFFE1E4FF),
    tertiary = Color(0xFFE77B92),
    onTertiary = Color(0xFF3D0716),
    background = Color(0xFF161A30),
    onBackground = Color(0xFFEDEAF6),
    surface = Color(0xFF161A30),
    onSurface = Color(0xFFEDEAF6),
    surfaceVariant = Color(0xFF262B48),
    onSurfaceVariant = Color(0xFFB9BDD8),
    surfaceContainerLowest = Color(0xFF10132A),
    surfaceContainerLow = Color(0xFF1C2038),
    surfaceContainer = Color(0xFF20253F),
    surfaceContainerHigh = Color(0xFF272C48),
    surfaceContainerHighest = Color(0xFF2F3552),
    outline = Color(0xFF5A6088),
    outlineVariant = Color(0xFF353B5C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF8A560A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB0),
    onPrimaryContainer = Color(0xFF2B1700),
    secondary = Color(0xFF4D5378),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE1FF),
    onSecondaryContainer = Color(0xFF161B3A),
    tertiary = Color(0xFFA23A55),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF3F4FA),
    onBackground = Color(0xFF1A1D33),
    surface = Color(0xFFF3F4FA),
    onSurface = Color(0xFF1A1D33),
    surfaceVariant = Color(0xFFE1E3F0),
    onSurfaceVariant = Color(0xFF464A62),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFBFE),
    surfaceContainer = Color(0xFFECEDF6),
    surfaceContainerHigh = Color(0xFFE6E7F2),
    surfaceContainerHighest = Color(0xFFE0E2EE),
    outline = Color(0xFF767A94),
    outlineVariant = Color(0xFFC6C8DA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

/** Fraunces (soft optical size, tabular digits baked in) for the timer and headings; system font for the rest. */
val Fraunces = FontFamily(
    Font(R.font.fraunces_light, FontWeight.Light),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
)

private val base = Typography()
private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.Light),
    displayMedium = base.displayMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.Light),
    displaySmall = base.displaySmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.Light),
    headlineLarge = base.headlineLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun FastwiseTheme(dark: Boolean, pureBlack: Boolean, dynamic: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val useDynamic = dynamic && Build.VERSION.SDK_INT >= 31
    var scheme: ColorScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    if (dark && pureBlack) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0C0D16),
            surfaceContainer = Color(0xFF12141F),
            surfaceContainerHigh = Color(0xFF191B29),
            surfaceContainerHighest = Color(0xFF202335),
        )
    }
    val fast = when {
        useDynamic -> (if (dark) DarkFast else LightFast).copy(
            arcStart = scheme.primary,
            arcEnd = lerp(scheme.primary, if (dark) Color.White else Color.Black, 0.35f),
            meal = scheme.tertiary,
            night = scheme.outlineVariant,
            track = scheme.surfaceVariant,
        )
        dark && pureBlack -> BlackFast
        dark -> DarkFast
        else -> LightFast
    }
    CompositionLocalProvider(LocalFastColors provides fast) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
