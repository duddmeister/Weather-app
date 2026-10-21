package io.github.duddmeister.weather.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.BuildConfig
import io.github.duddmeister.weather.model.AppSettings
import io.github.duddmeister.weather.model.AqiScale
import io.github.duddmeister.weather.model.DistanceUnit
import io.github.duddmeister.weather.model.PrecipitationUnit
import io.github.duddmeister.weather.model.PressureUnit
import io.github.duddmeister.weather.model.TemperatureUnit
import io.github.duddmeister.weather.model.TimeFormat
import io.github.duddmeister.weather.model.WindUnit
import io.github.duddmeister.weather.ui.components.CardLabel
import io.github.duddmeister.weather.ui.components.GlassCard
import io.github.duddmeister.weather.ui.theme.SkyText
import io.github.duddmeister.weather.update.UpdateState

@Composable
fun SettingsScreen(
    settings: AppSettings,
    updateState: UpdateState,
    updatesEnabled: Boolean,
    releasesUrl: String,
    hasBackgroundLocation: Boolean,
    onBack: () -> Unit,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBackgroundLocation: (Boolean) -> Unit,
    onNotificationToggle: (enabled: Boolean, apply: (AppSettings) -> AppSettings) -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "units") {
                Section(Icons.Rounded.Straighten, "Units") {
                    Choice("Temperature", TemperatureUnit.entries, settings.units.temperature, { it.label }) { v ->
                        onChange { it.copy(units = it.units.copy(temperature = v)) }
                    }
                    Choice("Wind", WindUnit.entries, settings.units.wind, { it.label }) { v ->
                        onChange { it.copy(units = it.units.copy(wind = v)) }
                    }
                    Choice("Precipitation", PrecipitationUnit.entries, settings.units.precipitation, { it.label }) { v ->
                        onChange { it.copy(units = it.units.copy(precipitation = v)) }
                    }
                    Choice("Pressure", PressureUnit.entries, settings.units.pressure, { it.label }) { v ->
                        onChange { it.copy(units = it.units.copy(pressure = v)) }
                    }
                    Choice("Distance", DistanceUnit.entries, settings.units.distance, { it.label }) { v ->
                        onChange { it.copy(units = it.units.copy(distance = v)) }
                    }
                    Choice("Time", TimeFormat.entries, settings.timeFormat, { it.label }) { v ->
                        onChange { it.copy(timeFormat = v) }
                    }
                }
            }
            item(key = "refresh") {
                Section(Icons.Rounded.Sync, "Freshness") {
                    Choice(
                        "Background refresh",
                        AppSettings.REFRESH_INTERVALS,
                        settings.refreshIntervalMinutes.takeIf { it in AppSettings.REFRESH_INTERVALS } ?: 15,
                        { minutes -> if (minutes == 0) "Off" else if (minutes < 60) "${minutes}m" else "${minutes / 60}h" },
                    ) { v -> onChange { it.copy(refreshIntervalMinutes = v) } }
                    Description("How often widgets and alerts update while the app is closed. Android may delay this when the battery is low; the widget's refresh button always works.")
                    Divider()
                    Toggle(
                        title = "Follow me in the background",
                        subtitle = if (hasBackgroundLocation) {
                            "Widgets update to wherever you are, even when Nimbus is closed."
                        } else {
                            "Needs location access set to \"Allow all the time\"."
                        },
                        checked = settings.backgroundLocation && hasBackgroundLocation,
                        onChange = onBackgroundLocation,
                    )
                }
            }
            item(key = "notifications") {
                Section(Icons.Rounded.Notifications, "Notifications") {
                    Toggle("Rain & snow alerts", "A heads-up about 15–45 minutes before precipitation starts where you are.", settings.precipitationAlerts) { on ->
                        onNotificationToggle(on) { it.copy(precipitationAlerts = on) }
                    }
                    Divider()
                    Toggle("Severe weather alerts", "Watches and warnings from the National Weather Service (US).", settings.severeAlerts) { on ->
                        onNotificationToggle(on) { it.copy(severeAlerts = on) }
                    }
                    Divider()
                    Toggle("App updates", "Tells you when a new version is published on GitHub.", settings.updateNotifications) { on ->
                        onNotificationToggle(on) { it.copy(updateNotifications = on) }
                    }
                }
            }
            item(key = "appearance") {
                Section(Icons.Rounded.Palette, "Appearance") {
                    Toggle("Animated sky", "Rain, snow, stars and clouds move in the background.", settings.animatedBackground) { on ->
                        onChange { it.copy(animatedBackground = on) }
                    }
                    Divider()
                    Choice("Air quality index", AqiScale.entries, settings.aqiScale, { it.label }) { v -> onChange { it.copy(aqiScale = v) } }
                }
            }
            item(key = "about") {
                Section(Icons.Rounded.Info, "About") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Nimbus ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(updateStatus(updateState, updatesEnabled), style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
                        }
                        when {
                            !updatesEnabled -> Unit
                            updateState is UpdateState.Checking -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            updateState is UpdateState.Available || updateState is UpdateState.Failed && updateState.release != null ->
                                TextButton(onClick = onOpenUpdate) { Text("Update", color = Color.White) }
                            else -> TextButton(onClick = onCheckForUpdates) { Text("Check now", color = Color.White) }
                        }
                    }
                    Divider()
                    LinkRow("Release notes & downloads") { uri.openUri(releasesUrl) }
                    LinkRow("Weather data: Open-Meteo.com") { uri.openUri("https://open-meteo.com/") }
                    LinkRow("Alerts: National Weather Service") { uri.openUri("https://www.weather.gov/") }
                }
            }
        }
    }
}

private fun updateStatus(state: UpdateState, enabled: Boolean): String = when {
    !enabled -> "Debug build · updates are disabled"
    state is UpdateState.Checking -> "Checking for updates…"
    state is UpdateState.Available -> "Version ${state.release.version} is available"
    state is UpdateState.Downloading -> "Downloading ${state.release.version}…"
    state is UpdateState.Installing -> "Installing ${state.release.version}…"
    state is UpdateState.Failed -> state.message
    state is UpdateState.UpToDate -> "You're up to date"
    else -> "Updates come from GitHub Releases"
}

@Composable
private fun Section(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(icon, title)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.08f))

@Composable
private fun Description(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = SkyText.Tertiary, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
}

@Composable
private fun <T> Choice(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Color.White.copy(alpha = 0.26f),
                        activeContentColor = Color.White,
                        activeBorderColor = Color.White.copy(alpha = 0.35f),
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = SkyText.Secondary,
                        inactiveBorderColor = Color.White.copy(alpha = 0.2f),
                    ),
                    icon = {},
                ) {
                    Text(label(option), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF4F8EF7), checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = SkyText.Tertiary, modifier = Modifier.size(16.dp))
    }
}
