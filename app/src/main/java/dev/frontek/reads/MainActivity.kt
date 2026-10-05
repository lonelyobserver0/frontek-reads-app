package dev.frontek.reads

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dev.frontek.reads.ui.AppViewModel
import dev.frontek.reads.ui.FrontekApp
import dev.frontek.reads.ui.theme.FrontekTheme

/** AppCompatActivity (not ComponentActivity) so per-app language changes apply on every API level. */
class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FrontekTheme { FrontekApp(vm) } }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let(vm::handleSharedText)
            // https://feeds.frontek.dev/?read=<url>&t=<title>&s=<source>
            Intent.ACTION_VIEW -> intent.data?.getQueryParameter("read")?.takeIf { it.isNotBlank() }?.let { link ->
                vm.openFromLink(link, intent.data?.getQueryParameter("t"), intent.data?.getQueryParameter("s"))
            }
        }
    }
}
