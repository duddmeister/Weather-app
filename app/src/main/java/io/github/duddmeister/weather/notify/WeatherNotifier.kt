package io.github.duddmeister.weather.notify

import android.content.Context
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import io.github.duddmeister.weather.data.local.SettingsStore
import io.github.duddmeister.weather.domain.NowcastBuilder
import io.github.duddmeister.weather.domain.TimeText
import io.github.duddmeister.weather.domain.zoneId
import io.github.duddmeister.weather.model.AppSettings
import io.github.duddmeister.weather.model.SavedLocation
import io.github.duddmeister.weather.model.TimeFormat
import io.github.duddmeister.weather.model.WeatherAlert
import io.github.duddmeister.weather.model.WeatherSnapshot
import kotlin.math.abs

/** Decides when fresh data deserves a notification: imminent rain/snow and new severe-weather alerts. */
class WeatherNotifier(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    suspend fun evaluate(
        primary: SavedLocation?,
        locations: List<SavedLocation>,
        snapshots: Map<String, WeatherSnapshot>,
        settings: AppSettings,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!Notifications.canPost(context)) return
        if (settings.precipitationAlerts && primary != null) {
            snapshots[primary.id]?.let { precipitation(primary, it, settings, nowMillis) }
        }
        if (settings.severeAlerts) severe(locations, snapshots, settings, nowMillis)
    }

    private suspend fun precipitation(location: SavedLocation, snapshot: WeatherSnapshot, settings: AppSettings, nowMillis: Long) {
        // Only act on data fetched in the last half hour; old nowcasts are guesses.
        if (nowMillis - snapshot.fetchedAt > 30 * 60_000L) return
        val nowcast = NowcastBuilder.build(snapshot, nowMillis) ?: return
        val startsIn = nowcast.startsInMinutes ?: return
        if (!nowcast.hasPrecipitation || nowcast.precipitatingNow || startsIn > 45) return

        val nowSec = nowMillis / 1000
        val eventStart = nowSec + startsIn * 60L
        val (lastStart, lastNotifiedAt) = settingsStore.lastPrecipitationNotification()
        if (abs(eventStart - lastStart) < 3600 && nowSec - lastNotifiedAt < 3 * 3600) return

        val time = TimeText(snapshot.zoneId(), use24h(settings)).time(eventStart)
        val builder = Notifications.builder(context, Notifications.CHANNEL_PRECIPITATION)
            .setContentTitle(nowcast.headline)
            .setContentText("Expected around $time in ${location.name}.")
            .setContentIntent(Notifications.openAppIntent(context, 1, locationId = location.id))
            .setTimeoutAfter(90 * 60_000L)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
        Notifications.post(context, Notifications.ID_PRECIPITATION, builder)
        settingsStore.setLastPrecipitationNotification(eventStart, nowSec)
    }

    private suspend fun severe(
        locations: List<SavedLocation>,
        snapshots: Map<String, WeatherSnapshot>,
        settings: AppSettings,
        nowMillis: Long,
    ) {
        val active: List<Pair<SavedLocation, WeatherAlert>> = locations.flatMap { location ->
            snapshots[location.id]?.alerts.orEmpty()
                .filter { (it.endTime ?: Long.MAX_VALUE) > nowMillis / 1000 }
                .map { location to it }
        }
        val alreadyNotified = settingsStore.notifiedAlertIds()
        val fresh = active.filter { (_, alert) -> alert.id !in alreadyNotified && alert.severityRank >= 2 }
        for ((location, alert) in fresh) {
            val snapshot = snapshots[location.id] ?: continue
            val until = alert.endTime?.let { " until " + TimeText(snapshot.zoneId(), use24h(settings)).timeWithDay(it, nowMillis) }.orEmpty()
            val details = alert.headline ?: alert.description?.lineSequence()?.firstOrNull { it.isNotBlank() }
            val builder = Notifications.builder(context, Notifications.CHANNEL_SEVERE)
                .setContentTitle("${alert.event} · ${location.name}")
                .setContentText("In effect$until")
                .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull("In effect$until.", details).joinToString("\n")))
                .setContentIntent(Notifications.openAppIntent(context, Notifications.alertId(alert.id), locationId = location.id))
                .setPriority(if (alert.severityRank >= 3) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
            Notifications.post(context, Notifications.alertId(alert.id), builder)
        }
        // Remember what we've shown, but only for alerts that are still active so the set stays small.
        val activeIds = active.map { it.second.id }.toSet()
        settingsStore.setNotifiedAlertIds((alreadyNotified + fresh.map { it.second.id }).intersect(activeIds))
    }

    private fun use24h(settings: AppSettings): Boolean = when (settings.timeFormat) {
        TimeFormat.H24 -> true
        TimeFormat.H12 -> false
        TimeFormat.System -> DateFormat.is24HourFormat(context)
    }
}
