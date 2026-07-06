package dev.frontek.feeds

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.frontek.feeds.ui.AppRoot
import dev.frontek.feeds.ui.AppViewModel
import dev.frontek.feeds.ui.theme.FrontekReadsTheme
import dev.frontek.feeds.ui.theme.ThemeMode

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val darkTheme = when (vm.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }
            FrontekReadsTheme(darkTheme = darkTheme, dynamicColor = vm.dynamicColor) {
                // Apply the user's font-size preference on top of the system font scale.
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, base.fontScale * vm.fontScale),
                ) {
                    AppRoot(vm)
                }
            }
        }
    }
}
