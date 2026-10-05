package dev.frontek.reads.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The frontek.dev palette (see css/style.css on the site). */
object Brand {
    val Teal = Color(0xFF2A9D8F)
    val Coral = Color(0xFFE76F51)
    val Dark = Color(0xFF264653)
    val Gold = Color(0xFFE9C46A)
    val Green = Color(0xFF8AB17D)
    val Cream = Color(0xFFF8F6EF)
    val Ink = Color(0xFF21333B)
    val Grey = Color(0xFF5A6B73)
    val Line = Color(0xFFE4E0D4)
    val Danger = Color(0xFFC0432C)
}

/** Colors outside the Material scheme: the dark header band, card titles, warnings. */
@Immutable
data class FrontekColors(
    val header: Color,
    val onHeader: Color,
    val onHeaderMuted: Color,
    val title: Color,
    val muted: Color,
    val line: Color,
    val chip: Color,
    val readCard: Color,
    val warnBg: Color,
    val warnFg: Color,
    val dark: Boolean,
)

val LocalFrontekColors = staticCompositionLocalOf {
    FrontekColors(Brand.Dark, Color.White, Color(0xFFB9CDD2), Brand.Dark, Brand.Grey, Brand.Line, Brand.Cream, Color(0xFFFCFCFA), Color(0xFFFDF3E3), Color(0xFF7A4A1E), false)
}

private val LightScheme = lightColorScheme(
    primary = Brand.Teal,
    onPrimary = Color.White,
    secondary = Brand.Coral,
    onSecondary = Color.White,
    tertiary = Brand.Gold,
    onTertiary = Brand.Dark,
    background = Color.White,
    onBackground = Brand.Ink,
    surface = Color.White,
    onSurface = Brand.Ink,
    surfaceVariant = Brand.Cream,
    onSurfaceVariant = Brand.Grey,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Brand.Cream,
    secondaryContainer = Color(0xFFD7EFEC),
    onSecondaryContainer = Brand.Dark,
    outline = Color(0xFFCFCABB),
    outlineVariant = Brand.Line,
    error = Brand.Danger,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF45B9AA),
    onPrimary = Color(0xFF00201C),
    secondary = Color(0xFFF08A6E),
    tertiary = Brand.Gold,
    onTertiary = Brand.Dark,
    background = Color(0xFF121A1E),
    onBackground = Color(0xFFE3EBEE),
    surface = Color(0xFF121A1E),
    onSurface = Color(0xFFE3EBEE),
    surfaceVariant = Color(0xFF1B262C),
    onSurfaceVariant = Color(0xFF9FB0B7),
    surfaceContainer = Color(0xFF172126),
    surfaceContainerLow = Color(0xFF172126),
    surfaceContainerHigh = Color(0xFF1E2A30),
    secondaryContainer = Color(0xFF1F4A45),
    onSecondaryContainer = Color(0xFFD7EFEC),
    outline = Color(0xFF3A4A52),
    outlineVariant = Color(0xFF2A373E),
    error = Color(0xFFFF8A73),
)

private val Base = Typography()
private val AppTypography = Typography(
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Bold),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 20.sp),
)

val KickerStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)

@Composable
fun FrontekTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val extra = if (dark) FrontekColors(
        header = Color(0xFF1B3540), onHeader = Color.White, onHeaderMuted = Color(0xFF9DB8C0),
        title = Color(0xFFEAF2F4), muted = Color(0xFF9FB0B7), line = Color(0xFF2A373E),
        chip = Color(0xFF1E2A30), readCard = Color(0xFF151E22),
        warnBg = Color(0xFF3A2C16), warnFg = Color(0xFFF2D49B), dark = true,
    ) else LocalFrontekColors.current
    CompositionLocalProvider(LocalFrontekColors provides extra) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography, content = content)
    }
}
