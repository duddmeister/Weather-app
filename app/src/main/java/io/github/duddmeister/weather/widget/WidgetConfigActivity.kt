package io.github.duddmeister.weather.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.duddmeister.weather.MainActivity
import io.github.duddmeister.weather.container
import io.github.duddmeister.weather.core.AppLog
import io.github.duddmeister.weather.model.SavedLocation
import io.github.duddmeister.weather.ui.components.GlassCard
import io.github.duddmeister.weather.ui.theme.NimbusTheme
import io.github.duddmeister.weather.ui.theme.SkyText
import kotlinx.coroutines.launch

/** Lets each widget follow a specific place (or the first place in the list, the default). */
class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            NimbusTheme {
                val locations by container.locations.displayLocations.collectAsStateWithLifecycle(initialValue = emptyList())
                WidgetConfigScreen(
                    locations = locations,
                    onPick = ::save,
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        save(null)
                    },
                )
            }
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private fun save(locationId: String?) {
        lifecycleScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(this@WidgetConfigActivity, glanceId) { prefs ->
                    if (locationId == null) prefs.remove(WidgetKeys.LOCATION_ID) else prefs[WidgetKeys.LOCATION_ID] = locationId
                }
                val provider = AppWidgetManager.getInstance(this@WidgetConfigActivity).getAppWidgetInfo(appWidgetId)?.provider?.className
                val widget = if (provider == ForecastWidgetReceiver::class.java.name) ForecastWidget() else NowWidget()
                widget.update(this@WidgetConfigActivity, glanceId)
                // Fetch fresh data for the chosen place without keeping this screen open.
                container.appScope.launch { container.refresher.refreshBackground() }
            } catch (e: Exception) {
                AppLog.w("WidgetConfig", "Couldn't configure widget $appWidgetId", e)
            }
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }
}

@Composable
private fun WidgetConfigScreen(locations: List<SavedLocation>, onPick: (String?) -> Unit, onOpenApp: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F2A5C), Color(0xFF2F6DB5)))),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("Choose a place", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("This widget will show the weather for:", style = MaterialTheme.typography.bodyMedium, color = SkyText.Secondary)
            Spacer(Modifier.height(16.dp))
            if (locations.isEmpty()) {
                Text(
                    "You haven't added any places yet. Open Nimbus to use your location or search for a city; the widget will follow it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenApp) { Text("Open Nimbus") }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Option(Icons.Rounded.Layers, "First place in my list", "Follows whichever place is first in Nimbus") { onPick(null) }
                    }
                    items(locations, key = { it.id }) { location ->
                        Option(
                            if (location.isCurrent) Icons.Rounded.NearMe else Icons.Rounded.Place,
                            if (location.isCurrent) "My location" else location.name,
                            if (location.isCurrent) location.name else location.subtitle,
                        ) { onPick(location.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Option(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
            }
        }
    }
}
