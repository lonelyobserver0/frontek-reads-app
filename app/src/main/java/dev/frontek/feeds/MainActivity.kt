package dev.frontek.feeds

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.frontek.feeds.ui.AppRoot
import dev.frontek.feeds.ui.AppViewModel
import dev.frontek.feeds.ui.theme.FrontekReadsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrontekReadsTheme {
                val vm: AppViewModel = viewModel()
                AppRoot(vm)
            }
        }
    }
}
