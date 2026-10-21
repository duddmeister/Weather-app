package io.github.duddmeister.weather.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import io.github.duddmeister.weather.BuildConfig
import io.github.duddmeister.weather.container
import io.github.duddmeister.weather.notify.Notifications
import io.github.duddmeister.weather.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives PackageInstaller session results. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val confirmation = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            null
        }
        context.container.updates.onInstallStatus(status, message, confirmation)
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "io.github.duddmeister.weather.action.INSTALL_STATUS"
    }
}

/** After an update installs: refresh widgets right away and confirm the new version. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                WidgetUpdater.updateAll(context)
                val builder = Notifications.builder(context, Notifications.CHANNEL_UPDATES)
                    .setContentTitle("Nimbus updated to ${BuildConfig.VERSION_NAME}")
                    .setContentText("Tap to open.")
                    .setContentIntent(Notifications.openAppIntent(context, 12))
                    .setTimeoutAfter(6 * 60 * 60 * 1000L)
                Notifications.post(context, Notifications.ID_UPDATED, builder)
            } finally {
                pending.finish()
            }
        }
    }
}
