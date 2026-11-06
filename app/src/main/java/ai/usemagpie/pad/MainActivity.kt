package ai.usemagpie.pad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ai.usemagpie.pad.ui.App
import ai.usemagpie.pad.ui.MagpieTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 调试版允许 chrome://inspect 远程调试控制台 WebView
        if (0 != (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)) android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        setContent { MagpieTheme { App(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.checkAll()
    }
}
