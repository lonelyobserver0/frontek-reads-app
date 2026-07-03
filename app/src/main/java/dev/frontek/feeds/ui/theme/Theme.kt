package dev.frontek.feeds.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// frontek reads brand palette
val BrandTeal = Color(0xFF2A9D8F)
val BrandCoral = Color(0xFFE76F51)
val BrandDark = Color(0xFF264653)
val BrandGold = Color(0xFFE9C46A)
val BrandGreen = Color(0xFF8AB17D)
val BrandCream = Color(0xFFF8F6EF)
val BrandInk = Color(0xFF21333B)
val BrandGrey = Color(0xFF5A6B73)
val BrandLine = Color(0xFFE4E0D4)
val BrandDanger = Color(0xFFC0432C)

private val LightColors = lightColorScheme(
    primary = BrandTeal,
    onPrimary = Color.White,
    secondary = BrandCoral,
    onSecondary = Color.White,
    tertiary = BrandGold,
    background = BrandCream,
    onBackground = BrandInk,
    surface = Color.White,
    onSurface = BrandInk,
    surfaceVariant = BrandCream,
    onSurfaceVariant = BrandGrey,
    outline = BrandLine,
    error = BrandDanger,
)

private val DarkColors = darkColorScheme(
    primary = BrandTeal,
    onPrimary = BrandDark,
    secondary = BrandCoral,
    onSecondary = Color.White,
    tertiary = BrandGold,
    background = BrandDark,
    onBackground = BrandCream,
    surface = Color(0xFF1E3640),
    onSurface = BrandCream,
    surfaceVariant = Color(0xFF2E4C58),
    onSurfaceVariant = Color(0xFFB9C6CC),
    outline = Color(0xFF3A5560),
    error = Color(0xFFE7897A),
)

@Composable
fun FrontekReadsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
