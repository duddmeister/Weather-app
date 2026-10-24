package io.github.duddmeister.weather.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.duddmeister.weather.MainActivity
import io.github.duddmeister.weather.R

object Notifications {
    const val CHANNEL_SEVERE = "severe_alerts"
    const val CHANNEL_PRECIPITATION = "precipitation"
    const val CHANNEL_UPDATES = "updates"

    const val ID_PRECIPITATION = 1001
    const val ID_UPDATE_AVAILABLE = 1002
    const val ID_UPDATED = 1003
    private const val ID_ALERT_BASE = 2000

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_SEVERE, "Severe weather alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Official weather warnings for your places: the Met Office and other European services, and the US National Weather Service"
                },
                NotificationChannel(CHANNEL_PRECIPITATION, "Rain & snow alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A heads-up shortly before rain or snow starts where you are"
                },
                NotificationChannel(CHANNEL_UPDATES, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When a new version of Nimbus is ready to install"
                },
            ),
        )
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun alertId(alertId: String): Int = ID_ALERT_BASE + (alertId.hashCode() and 0xFFFF)

    /** Opens the app; [action] and [locationId] are handled by MainActivity. */
    fun openAppIntent(context: Context, requestCode: Int, action: String? = null, locationId: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action ?: Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (locationId != null) putExtra(MainActivity.EXTRA_LOCATION_ID, locationId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun builder(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_nimbus)
            .setColor(0xFF2F7FD6.toInt())
            .setAutoCancel(true)
}
