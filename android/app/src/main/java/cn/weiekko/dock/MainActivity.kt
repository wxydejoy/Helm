package cn.weiekko.dock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cn.weiekko.dock.data.HubConnection
import cn.weiekko.dock.power.DockPower
import cn.weiekko.dock.ui.DockTheme
import cn.weiekko.dock.ui.DockViewModel
import cn.weiekko.dock.ui.HomeScreen
import cn.weiekko.dock.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val viewModel: DockViewModel by viewModels()
    private var powerReceiverRegistered = false
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!viewModel.ui.value.powerScreen) return
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> DockPower.wake(this@MainActivity)
                Intent.ACTION_POWER_DISCONNECTED -> DockPower.sleep(this@MainActivity)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            LaunchedEffect(state.powerScreen) {
                applyPowerState()
            }

            DockTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
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
                                    onSelectModule = viewModel::selectModule,
                                    onMoveModule = viewModel::moveModule,
                                    onResizeModule = viewModel::resizeModule,
                                    onHideModule = viewModel::hideModule,
                                    onToggleModuleChrome = viewModel::toggleModuleChrome,
                                    onExitEdit = viewModel::exitEdit,
                                    onResetLayout = viewModel::resetLayout,
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    state = state,
                                    canGoBack = true,
                                    onTest = viewModel::testConnection,
                                    onSave = viewModel::saveDraft,
                                    onSetVideo = viewModel::setBackgroundVideo,
                                    onSetTileLook = viewModel::setTileLook,
                                    onSetTypeLook = viewModel::setTypeLook,
                                    onSetPowerScreen = viewModel::setPowerScreen,
                                    onEditLayout = viewModel::enterEdit,
                                    onSetModuleVisible = viewModel::setModuleVisible,
                                    onSetModuleChrome = viewModel::setModuleChrome,
                                    onResetLayout = viewModel::resetLayout,
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

    override fun onStart() {
        super.onStart()
        if (!powerReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            ContextCompat.registerReceiver(
                this,
                powerReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            powerReceiverRegistered = true
        }
        applyPowerState()
    }

    override fun onStop() {
        if (powerReceiverRegistered) {
            unregisterReceiver(powerReceiver)
            powerReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        applyPowerState()
    }

    private fun applyPowerState() {
        if (!viewModel.ui.value.powerScreen) {
            DockPower.keepScreenOn(this, false)
            return
        }
        if (DockPower.isPlugged(this)) {
            DockPower.wake(this)
        } else {
            DockPower.keepScreenOn(this, false)
        }
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
