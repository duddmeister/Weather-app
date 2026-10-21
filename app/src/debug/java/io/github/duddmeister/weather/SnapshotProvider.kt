package io.github.duddmeister.weather

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import java.io.File
import java.lang.ref.WeakReference

/**
 * Debug-only: saves the visible screen as a PNG on request, so CI can show what the UI looks like.
 * The view hierarchy is drawn in software, which works on emulators where `screencap` hangs.
 *
 *   adb shell am broadcast -p <package> -a <package>.SNAPSHOT --es name home
 *   adb exec-out run-as <package> cat files/snapshots/home.png > home.png
 *
 * A content provider is used only because the system creates it at startup without any hook in main code.
 */
class SnapshotProvider : ContentProvider() {
    private var resumed: WeakReference<Activity>? = null

    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumed = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (resumed?.get() === activity) resumed = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val name = intent.getStringExtra("name") ?: "snapshot"
                // `am broadcast` prints this, so the caller knows whether the file exists.
                resultData = runCatching { save(name) }.getOrElse { "failed: $it" }
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter("${app.packageName}.SNAPSHOT"), ContextCompat.RECEIVER_EXPORTED)
        return true
    }

    private fun save(name: String): String {
        val activity = resumed?.get() ?: return "failed: no visible activity"
        val bitmap = activity.window.decorView.drawToBitmap()
        val directory = File(activity.filesDir, "snapshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "saved"
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
