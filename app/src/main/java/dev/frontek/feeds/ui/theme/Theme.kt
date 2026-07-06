package dev.frontek.feeds.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Persisted theme preference values. */
object ThemeMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
}

/** True when Material You dynamic color is available on this device (Android 12+). */
val dynamicColorAvailable: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Whether the app is currently rendering its dark theme (honors the user's override). */
val LocalAppDarkTheme = staticCompositionLocalOf { false }

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
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && dynamicColorAvailable -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalAppDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content,
        )
    }
}
