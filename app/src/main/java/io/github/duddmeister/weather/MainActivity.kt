package io.github.duddmeister.weather

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.duddmeister.weather.core.AppLog
import io.github.duddmeister.weather.ui.LaunchRequest
import io.github.duddmeister.weather.ui.MainViewModel
import io.github.duddmeister.weather.ui.NimbusRoot
import io.github.duddmeister.weather.ui.Screen
import io.github.duddmeister.weather.ui.theme.NimbusTheme
import io.github.duddmeister.weather.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val launchRequests = MutableStateFlow<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Hold the splash until cached weather is on screen (it loads from disk in milliseconds).
        val started = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            !viewModel.uiState.value.ready && SystemClock.uptimeMillis() - started < 1_500
        }
        if (savedInstanceState == null) handleIntent(intent)

        // The system's "update this app?" confirmation must be started from a visible activity.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.updates.pendingConfirmation.collect { confirmation ->
                    if (confirmation != null) {
                        container.updates.consumeConfirmation()
                        runCatching { startActivity(confirmation) }
                            .onFailure { AppLog.e("MainActivity", "Couldn't show install confirmation", it) }
                    }
                }
            }
        }

        setContent {
            NimbusTheme {
                val request by launchRequests.collectAsStateWithLifecycle()
                NimbusRoot(viewModel, request) { launchRequests.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val locationId = intent.getStringExtra(EXTRA_LOCATION_ID)
        val showUpdate = intent.action == UpdateManager.ACTION_SHOW_UPDATE
        val installUpdate = intent.action == UpdateManager.ACTION_INSTALL_UPDATE
        val screen = when (intent.action) {
            ACTION_OPEN_PLACES -> Screen.Places
            ACTION_OPEN_SETTINGS -> Screen.Settings
            else -> null
        }
        if (locationId != null || showUpdate || installUpdate || screen != null) {
            launchRequests.value = LaunchRequest(locationId, showUpdate, installUpdate, screen)
        }
    }

    companion object {
        const val EXTRA_LOCATION_ID = "io.github.duddmeister.weather.extra.LOCATION_ID"
        const val ACTION_OPEN_PLACES = "io.github.duddmeister.weather.action.OPEN_PLACES"
        const val ACTION_OPEN_SETTINGS = "io.github.duddmeister.weather.action.OPEN_SETTINGS"
    }
}
