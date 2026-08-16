package cn.weiekko.dock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cn.weiekko.dock.data.HubConnection
import cn.weiekko.dock.ui.DockTheme
import cn.weiekko.dock.ui.DockViewModel
import cn.weiekko.dock.ui.HomeScreen
import cn.weiekko.dock.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val viewModel: DockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        hideSystemBars()
        applyHubExtras(intent)
        setContent {
            val state by viewModel.ui.collectAsStateWithLifecycle()
            val navController = rememberNavController()

            LaunchedEffect(viewModel) {
                viewModel.goSettings.collect {
                    navController.navigate("settings") {
                        launchSingleTop = true
                    }
                }
            }
            LaunchedEffect(viewModel) {
                viewModel.goHome.collect {
                    navController.navigate("home") {
                        popUpTo("settings") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            DockTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    if (!state.prefsReady) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            composable("home") {
                                HomeScreen(
                                    state = state,
                                    onOpenSettings = { navController.navigate("settings") },
                                    onPower = viewModel::setPower,
                                    onWinClick = viewModel::tapWinApp,
                                    onMedia = viewModel::sendMedia,
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    state = state,
                                    canGoBack = true,
                                    onTest = viewModel::testConnection,
                                    onSave = viewModel::saveDraft,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyHubExtras(intent)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun applyHubExtras(intent: Intent?) {
        val host = intent?.getStringExtra(EXTRA_HOST)?.trim().orEmpty()
        val token = intent?.getStringExtra(EXTRA_TOKEN)?.trim().orEmpty()
        if (host.isEmpty() || token.isEmpty()) return
        val port = intent?.getIntExtra(EXTRA_PORT, HubConnection.DEFAULT_PORT)
            ?: HubConnection.DEFAULT_PORT
        viewModel.testConnection(host, port.toString(), token)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_TOKEN = "token"
    }
}
