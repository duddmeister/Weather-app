package io.github.duddmeister.weather.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import io.github.duddmeister.weather.AppContainer
import io.github.duddmeister.weather.R
import io.github.duddmeister.weather.data.WidgetRefreshState
import io.github.duddmeister.weather.domain.TimeText
import io.github.duddmeister.weather.domain.conditionsAt
import io.github.duddmeister.weather.domain.isDaylightAt
import io.github.duddmeister.weather.domain.localDate
import io.github.duddmeister.weather.domain.today
import io.github.duddmeister.weather.domain.upcomingDays
import io.github.duddmeister.weather.domain.upcomingHours
import io.github.duddmeister.weather.domain.zoneId
import io.github.duddmeister.weather.model.AppSettings
import io.github.duddmeister.weather.model.SavedLocation
import io.github.duddmeister.weather.model.TimeFormat
import io.github.duddmeister.weather.model.Units
import io.github.duddmeister.weather.model.WeatherSnapshot
import io.github.duddmeister.weather.ui.SkyKind
import io.github.duddmeister.weather.ui.WeatherVisuals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class WidgetInputs(
    val locations: List<SavedLocation>,
    val snapshots: Map<String, WeatherSnapshot>,
    val settings: AppSettings,
    val refresh: WidgetRefreshState,
)

fun widgetInputs(container: AppContainer): Flow<WidgetInputs> = combine(
    container.locations.displayLocations,
    container.repository.snapshots,
    container.settingsStore.settings,
    container.refresher.widgetState,
) { locations, snapshots, settings, refresh -> WidgetInputs(locations, snapshots, settings, refresh) }

data class WidgetHour(val label: String, @DrawableRes val icon: Int, val temperature: String, val precipitation: String?)

data class WidgetDay(
    val label: String,
    @DrawableRes val icon: Int,
    val high: String,
    val low: String,
    val precipitation: String?,
    /** Position of this day's range within the week's range, 0..1. */
    val rangeStart: Float,
    val rangeEnd: Float,
)

data class WidgetModel(
    val locationId: String?,
    val locationName: String,
    val isCurrentLocation: Boolean,
    val temperature: String,
    val condition: String,
    @DrawableRes val icon: Int,
    val highLow: String,
    val feelsLike: String?,
    /** "Updated 3:42 PM", or a failure note. */
    val status: String,
    val statusIsWarning: Boolean,
    @DrawableRes val background: Int,
    val hours: List<WidgetHour>,
    val days: List<WidgetDay>,
    val refreshing: Boolean,
    val hasData: Boolean,
)

object WidgetModels {
    private const val STALE_AFTER_MILLIS = 60 * 60 * 1000L

    fun use24h(context: Context, settings: AppSettings): Boolean = when (settings.timeFormat) {
        TimeFormat.H24 -> true
        TimeFormat.H12 -> false
        TimeFormat.System -> DateFormat.is24HourFormat(context)
    }

    fun build(inputs: WidgetInputs, locationId: String?, nowMillis: Long, use24h: Boolean, deviceZone: ZoneId = ZoneId.systemDefault()): WidgetModel {
        val location = inputs.locations.firstOrNull { it.id == locationId } ?: inputs.locations.firstOrNull()
        val snapshot = location?.let { inputs.snapshots[it.id] }
        if (location == null || snapshot == null) {
            return WidgetModel(
                locationId = location?.id,
                locationName = location?.name ?: "Nimbus",
                isCurrentLocation = location?.isCurrent == true,
                temperature = "--°",
                condition = if (location == null) "Open Nimbus to pick a place" else "Tap refresh to load",
                icon = R.drawable.wx_partly_cloudy_day,
                highLow = "",
                feelsLike = null,
                status = if (inputs.refresh.refreshing) "Updating…" else "No data yet",
                statusIsWarning = false,
                background = WeatherVisuals.widgetBackground(SkyKind.CloudyDay),
                hours = emptyList(),
                days = emptyList(),
                refreshing = inputs.refresh.refreshing,
                hasData = false,
            )
        }

        val units = inputs.settings.units
        val now = snapshot.conditionsAt(nowMillis)
        val today = snapshot.today(nowMillis)
        val locationTime = TimeText(snapshot.zoneId(), use24h)
        val deviceTime = TimeText(deviceZone, use24h)

        val failedSinceData = inputs.refresh.lastFailureAt > snapshot.fetchedAt
        val stale = nowMillis - snapshot.fetchedAt > STALE_AFTER_MILLIS
        val updatedAt = deviceTime.timeMillis(snapshot.fetchedAt)
        val status = when {
            inputs.refresh.refreshing -> "Updating…"
            failedSinceData -> "Offline · $updatedAt"
            else -> "Updated $updatedAt"
        }

        val hours = snapshot.upcomingHours(nowMillis, count = 8).mapIndexed { index, hour ->
            if (index == 0) {
                WidgetHour("Now", WeatherVisuals.icon(now.weatherCode, now.isDay), Units.formatTemperature(now.temperature, units.temperature), null)
            } else {
                val isDay = snapshot.isDaylightAt(hour.time) ?: hour.isDay ?: true
                val chance = hour.precipitationProbability?.roundToInt()?.takeIf { it >= 20 }?.let { "$it%" }
                WidgetHour(locationTime.hour(hour.time), WeatherVisuals.icon(hour.weatherCode, isDay), Units.formatTemperature(hour.temperature, units.temperature), chance)
            }
        }

        val upcoming = snapshot.upcomingDays(nowMillis).take(6)
        val weekMin = upcoming.mapNotNull { it.temperatureMin }.minOrNull()
        val weekMax = upcoming.mapNotNull { it.temperatureMax }.maxOrNull()
        val span = if (weekMin != null && weekMax != null && weekMax > weekMin) weekMax - weekMin else null
        val localToday = snapshot.localDate(nowMillis)
        val days = upcoming.map { day ->
            val date = LocalDate.parse(day.date)
            val chance = day.precipitationProbabilityMax?.roundToInt()?.takeIf { it >= 20 }?.let { "$it%" }
            val start = if (span != null && day.temperatureMin != null) ((day.temperatureMin - weekMin!!) / span).toFloat() else 0f
            val end = if (span != null && day.temperatureMax != null) ((day.temperatureMax - weekMin!!) / span).toFloat() else 1f
            WidgetDay(
                label = locationTime.dayLabel(date, localToday),
                icon = WeatherVisuals.icon(day.weatherCode, isDay = true),
                high = Units.formatTemperature(day.temperatureMax, units.temperature),
                low = Units.formatTemperature(day.temperatureMin, units.temperature),
                precipitation = chance,
                rangeStart = start.coerceIn(0f, 1f),
                rangeEnd = end.coerceIn(0f, 1f),
            )
        }

        return WidgetModel(
            locationId = location.id,
            locationName = location.name,
            isCurrentLocation = location.isCurrent,
            temperature = Units.formatTemperature(now.temperature, units.temperature),
            condition = now.condition.label(now.isDay),
            icon = WeatherVisuals.icon(now.weatherCode, now.isDay),
            highLow = if (today != null) {
                "H ${Units.formatTemperature(today.temperatureMax, units.temperature)}  L ${Units.formatTemperature(today.temperatureMin, units.temperature)}"
            } else {
                ""
            },
            feelsLike = now.apparentTemperature?.let { "Feels like ${Units.formatTemperature(it, units.temperature)}" },
            status = status,
            statusIsWarning = failedSinceData || stale,
            background = WeatherVisuals.widgetBackground(WeatherVisuals.skyKind(snapshot, now, nowMillis)),
            hours = hours,
            days = days,
            refreshing = inputs.refresh.refreshing,
            hasData = true,
        )
    }
}
