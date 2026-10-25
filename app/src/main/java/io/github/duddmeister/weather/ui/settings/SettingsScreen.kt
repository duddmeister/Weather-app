package io.github.duddmeister.weather.ui.settings

import android.content.ClipboardManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.duddmeister.weather.BuildConfig
import io.github.duddmeister.weather.data.MetOfficeForecasts
import io.github.duddmeister.weather.data.MetOfficeState
import io.github.duddmeister.weather.data.MetOfficeStatus
import io.github.duddmeister.weather.domain.TimeText
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
import io.github.duddmeister.weather.ui.components.PulsingDots
import io.github.duddmeister.weather.ui.home.rememberUse24h
import io.github.duddmeister.weather.ui.theme.SkyText
import io.github.duddmeister.weather.update.UpdateState
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    updateState: UpdateState,
    updatesEnabled: Boolean,
    releasesUrl: String,
    hasBackgroundLocation: Boolean,
    unrestrictedBattery: Boolean,
    metOffice: MetOfficeState,
    onBack: () -> Unit,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBackgroundLocation: (Boolean) -> Unit,
    onAllowUnrestrictedBattery: () -> Unit,
    onNotificationToggle: (enabled: Boolean, apply: (AppSettings) -> AppSettings) -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenUpdate: () -> Unit,
    onMetOfficeKey: (String?) -> Unit,
) {
    val uri = LocalUriHandler.current
    val use24h = rememberUse24h(settings)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Spacer(Modifier.width(4.dp))
            Text("Settings", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        LazyColumn(
            modifier = Modifier.fadeTopEdge(16.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "units") {
                Section(Icons.Rounded.Straighten, "Units") {
                    ChoiceRow("Temperature", TemperatureUnit.entries, settings.units.temperature, { it.label }) { v ->
                        onChange { it.copy(units = it.units.copy(temperature = v)) }
                    }
                    RowDivider()
                    ChoiceRow("Wind", WindUnit.entries, settings.units.wind, { it.label }) { v ->
                        onChange { it.copy(units = it.units.copy(wind = v)) }
                    }
                    RowDivider()
                    ChoiceRow("Precipitation", PrecipitationUnit.entries, settings.units.precipitation, { it.label }) { v ->
                        onChange { it.copy(units = it.units.copy(precipitation = v)) }
                    }
                    RowDivider()
                    ChoiceRow("Pressure", PressureUnit.entries, settings.units.pressure, { it.label }) { v ->
                        onChange { it.copy(units = it.units.copy(pressure = v)) }
                    }
                    RowDivider()
                    ChoiceRow("Distance", DistanceUnit.entries, settings.units.distance, { it.label }) { v ->
                        onChange { it.copy(units = it.units.copy(distance = v)) }
                    }
                    RowDivider()
                    ChoiceRow("Time format", TimeFormat.entries, settings.timeFormat, ::timeFormatLabel) { v ->
                        onChange { it.copy(timeFormat = v) }
                    }
                }
            }
            item(key = "refresh") {
                Section(Icons.Rounded.Sync, "Freshness") {
                    ChoiceRow(
                        title = "Auto refresh",
                        subtitle = "While Nimbus is closed",
                        options = AppSettings.REFRESH_INTERVALS,
                        selected = settings.refreshIntervalMinutes.takeIf { it in AppSettings.REFRESH_INTERVALS } ?: 15,
                        label = { minutes -> if (minutes == 0) "Off" else if (minutes < 60) "${minutes}m" else "${minutes / 60}h" },
                    ) { v -> onChange { it.copy(refreshIntervalMinutes = v) } }
                    RowDivider()
                    SettingRow(
                        title = "Reliable updates",
                        subtitle = if (unrestrictedBattery) {
                            "On. Android won't delay Nimbus to save battery."
                        } else {
                            "Android may delay background refreshes to save battery, so widgets fall behind."
                        },
                        trailing = {
                            if (unrestrictedBattery) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "On", tint = Positive, modifier = Modifier.size(22.dp))
                            } else {
                                PillButton("Allow", onAllowUnrestrictedBattery)
                            }
                        },
                    )
                    RowDivider()
                    ToggleRow(
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
            item(key = "metoffice") {
                Section(Icons.Rounded.VpnKey, "Met Office forecasts") {
                    MetOfficeSettings(
                        state = metOffice,
                        use24h = use24h,
                        onGetKey = { uri.openUri(METOFFICE_DATAHUB_URL) },
                        onSetKey = onMetOfficeKey,
                    )
                }
            }
            item(key = "notifications") {
                Section(Icons.Rounded.Notifications, "Notifications") {
                    ToggleRow("Rain & snow alerts", "A heads-up 15–45 minutes before it starts where you are.", settings.precipitationAlerts) { on ->
                        onNotificationToggle(on) { it.copy(precipitationAlerts = on) }
                    }
                    RowDivider()
                    ToggleRow(
                        "Severe weather alerts",
                        "Official warnings from the Met Office, other European services and the US National Weather Service.",
                        settings.severeAlerts,
                    ) { on ->
                        onNotificationToggle(on) { it.copy(severeAlerts = on) }
                    }
                    RowDivider()
                    ToggleRow("App updates", "When a new version is published on GitHub.", settings.updateNotifications) { on ->
                        onNotificationToggle(on) { it.copy(updateNotifications = on) }
                    }
                }
            }
            item(key = "appearance") {
                Section(Icons.Rounded.Palette, "Appearance") {
                    ToggleRow("Animated sky", "Rain, snow, stars and clouds move behind the forecast.", settings.animatedBackground) { on ->
                        onChange { it.copy(animatedBackground = on) }
                    }
                    RowDivider()
                    ToggleRow(
                        "Animated widgets",
                        "The weather moves on home screen widgets too. Uses a little more battery while they're on screen.",
                        settings.animatedWidgets,
                    ) { on ->
                        onChange { it.copy(animatedWidgets = on) }
                    }
                    RowDivider()
                    ChoiceRow("Air quality index", AqiScale.entries, settings.aqiScale, ::aqiScaleLabel) { v -> onChange { it.copy(aqiScale = v) } }
                }
            }
            item(key = "about") {
                Section(Icons.Rounded.Info, "About") {
                    val updateAction: (@Composable () -> Unit)? = when {
                        !updatesEnabled -> null
                        updateState is UpdateState.Checking -> ({ PulsingDots(Color.White, Modifier.padding(horizontal = 12.dp)) })
                        updateState is UpdateState.Available || updateState is UpdateState.Failed && updateState.release != null ->
                            ({ PillButton("Update", onOpenUpdate) })
                        else -> ({ PillButton("Check now", onCheckForUpdates, filled = false) })
                    }
                    SettingRow(
                        title = "Nimbus ${BuildConfig.VERSION_NAME}",
                        subtitle = updateStatus(updateState, updatesEnabled),
                        trailing = updateAction,
                    )
                    RowDivider()
                    LinkRow("Release notes & downloads") { uri.openUri(releasesUrl) }
                    RowDivider()
                    LinkRow("Weather data: Open-Meteo") { uri.openUri("https://open-meteo.com/") }
                    if (metOffice.keyHint != null) {
                        RowDivider()
                        LinkRow("UK forecasts: Met Office") { uri.openUri("https://www.metoffice.gov.uk/") }
                    }
                    RowDivider()
                    LinkRow("Warnings: MeteoAlarm (CC BY 4.0)") { uri.openUri("https://meteoalarm.org/") }
                    RowDivider()
                    LinkRow("Alerts: National Weather Service") { uri.openUri("https://www.weather.gov/") }
                }
            }
        }
    }
}

private const val METOFFICE_DATAHUB_URL = "https://datahub.metoffice.gov.uk/"

/** Content fades out as it scrolls up under the title, instead of being cut off. */
private fun Modifier.fadeTopEdge(height: Dp) = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = (height.toPx() / size.height).coerceIn(0f, 1f)
        drawRect(Brush.verticalGradient(0f to Color.Transparent, fade to Color.Black), blendMode = BlendMode.DstIn)
    }

private val RowPadding = 16.dp
private val Accent = Color(0xFF4F8EF7)
private val Positive = Color(0xFF7FDDA0)

private val RowTitle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(letterSpacing = 0.1.sp)

private val RowSubtitle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp)

private fun timeFormatLabel(format: TimeFormat) = when (format) {
    TimeFormat.System -> "Auto"
    TimeFormat.H12 -> "12h"
    TimeFormat.H24 -> "24h"
}

private fun aqiScaleLabel(scale: AqiScale) = when (scale) {
    AqiScale.Auto -> "Auto"
    AqiScale.Us -> "US"
    AqiScale.European -> "EU"
}

@Composable
private fun MetOfficeSettings(state: MetOfficeState, use24h: Boolean, onGetKey: () -> Unit, onSetKey: (String?) -> Unit) {
    var addingKey by rememberSaveable { mutableStateOf(false) }
    if (state.keyHint == null) {
        Text(
            "Use the Met Office's own forecasts for places in the UK and Ireland, the same ones its app shows. " +
                "They need a free API key from the Met Office Weather DataHub.",
            style = RowSubtitle,
            color = SkyText.Secondary,
            modifier = Modifier.padding(horizontal = RowPadding, vertical = 8.dp),
        )
        Column(Modifier.padding(horizontal = RowPadding, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Step(1, "Tap Get a free key and sign up to the Weather DataHub.")
            Step(2, "Subscribe to the free Site Specific forecast plan.")
            Step(3, "Copy the plan's API key, then tap Add key and paste it.")
        }
        Row(Modifier.padding(horizontal = RowPadding, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Get a free key", onGetKey, filled = false)
            PillButton("Add key", { addingKey = true })
        }
    } else {
        val checking: (@Composable () -> Unit)? = if (state.status == MetOfficeStatus.Checking) {
            { PulsingDots(Color.White) }
        } else {
            null
        }
        SettingRow(
            title = "API key ••••${state.keyHint}",
            subtitle = metOfficeStatus(state, use24h, System.currentTimeMillis()),
            subtitleColor = if (state.status == MetOfficeStatus.Rejected) SkyText.Warning else SkyText.Secondary,
            leading = { StatusDot(metOfficeStatusColor(state)) },
            trailing = checking,
        )
        Row(Modifier.padding(start = RowPadding, end = RowPadding, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Replace key", { addingKey = true })
            PillButton("Remove", { onSetKey(null) }, filled = false)
        }
    }
    if (addingKey) {
        MetOfficeKeyDialog(
            onDismiss = { addingKey = false },
            onSave = { key ->
                addingKey = false
                onSetKey(key)
            },
        )
    }
}

private fun metOfficeStatus(state: MetOfficeState, use24h: Boolean, nowMillis: Long): String {
    val calls = "${state.callsToday} of 360 calls today"
    return when (state.status) {
        MetOfficeStatus.NoKey -> ""
        MetOfficeStatus.Checking -> "Checking the key…"
        MetOfficeStatus.Ready -> state.lastSuccessAt?.let { "Working · updated ${TimeText.relativeAge(nowMillis, it)} · $calls" }
            ?: "Ready for your places in the UK and Ireland"
        MetOfficeStatus.Rejected ->
            "The Met Office didn't accept this key. Check it was copied in full and is the one for your Site Specific plan."
        MetOfficeStatus.LimitReached -> {
            val reset = (MetOfficeForecasts.utcDay(nowMillis) + 1) * 86_400L
            "Today's calls are used up, so Open-Meteo fills in until " +
                TimeText(ZoneId.systemDefault(), use24h).timeWithDay(reset, nowMillis) + "."
        }
        MetOfficeStatus.Unavailable -> "Couldn't reach the Met Office just now, so Open-Meteo is filling in · $calls"
    }
}

private fun metOfficeStatusColor(state: MetOfficeState): Color = when (state.status) {
    MetOfficeStatus.Ready -> if (state.lastSuccessAt != null) Positive else SkyText.Tertiary
    MetOfficeStatus.Checking -> Color(0xFF9CCBFF)
    MetOfficeStatus.Rejected -> Color(0xFFFF8A80)
    MetOfficeStatus.LimitReached, MetOfficeStatus.Unavailable -> SkyText.Warning
    MetOfficeStatus.NoKey -> SkyText.Tertiary
}

@Composable
private fun MetOfficeKeyDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    val key = MetOfficeForecasts.cleanKey(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Met Office API key") },
        text = {
            Column {
                Text(
                    "Paste the API key from your Site Specific plan on the Weather DataHub. It stays on this phone and is only sent to the Met Office.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("API key") },
                    trailingIcon = {
                        IconButton(onClick = {
                            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                            clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.let { text = it }
                        }) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { key?.let(onSave) }, enabled = key != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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

/** A card of rows under a small heading; rows run edge to edge so their touch feedback fills the card. */
@Composable
private fun Section(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(top = 16.dp, bottom = 6.dp)) {
        CardLabel(icon, title, modifier = Modifier.padding(horizontal = RowPadding))
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(horizontal = RowPadding), thickness = 1.dp, color = Color.White.copy(alpha = 0.07f))

/** One row: a title, an optional line of detail, and an optional control at the end. */
@Composable
private fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = SkyText.Secondary,
    minHeight: Dp = 56.dp,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = RowPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = RowTitle, color = Color.White)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = RowSubtitle, color = subtitleColor)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    subtitle: String? = null,
    onSelect: (T) -> Unit,
) {
    SettingRow(title = title, subtitle = subtitle, trailing = { PillChoice(options, selected, label, onSelect) })
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.85f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.14f),
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        },
    )
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    SettingRow(
        title = title,
        modifier = Modifier.clickable(onClick = onClick),
        minHeight = 48.dp,
        trailing = { Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = SkyText.Tertiary, modifier = Modifier.size(18.dp)) },
    )
}

/** A rounded button: filled for the main action, outlined for the other. */
@Composable
private fun PillButton(text: String, onClick: () -> Unit, filled: Boolean = true) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(Color.White.copy(alpha = 0.18f))
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = RowSubtitle, color = SkyText.Secondary, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

/**
 * A compact segmented control: the options sit on a rounded track and a highlight slides to the
 * chosen one. Options share the widest one's width, shrinking if the row is too narrow.
 */
@Composable
private fun <T> PillChoice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val position by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "choice",
    )
    Layout(
        content = {
            Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.24f)))
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                Box(
                    Modifier
                        .clip(CircleShape)
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) Color.White else SkyText.Secondary,
                        maxLines = 1,
                    )
                }
            }
        },
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(3.dp)
            .selectableGroup(),
    ) { measurables, constraints ->
        val highlight = measurables.first()
        val segments = measurables.drop(1)
        val widest = segments.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val width = if (constraints.hasBoundedWidth) minOf(widest, constraints.maxWidth / segments.size) else widest
        val height = segments.maxOf { it.minIntrinsicHeight(width) }
        val placeables = segments.map { it.measure(Constraints.fixed(width, height)) }
        val pill = highlight.measure(Constraints.fixed(width, height))
        layout(width * segments.size, height) {
            pill.placeRelative((position * width).roundToInt(), 0)
            placeables.forEachIndexed { index, placeable -> placeable.placeRelative(index * width, 0) }
        }
    }
}
