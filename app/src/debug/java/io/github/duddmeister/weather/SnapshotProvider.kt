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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import java.io.File
import java.lang.ref.WeakReference

/**
 * Debug-only: saves the visible screen as a PNG on request, so CI can show what the UI looks like.
 * It copies the app's own window, which works on emulators where `screencap` hangs.
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
                val activity = resumed?.get()
                if (activity == null) {
                    resultData = "failed: no visible activity"
                    return
                }
                // `am broadcast` prints the result data, so the caller knows whether the file exists.
                val pending = goAsync()
                capture(activity) { bitmap ->
                    pending.resultData = runCatching { save(activity, name, bitmap()) }.getOrElse { "failed: $it" }
                    pending.finish()
                }
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter("${app.packageName}.SNAPSHOT"), ContextCompat.RECEIVER_EXPORTED)
        return true
    }

    /**
     * Copies what the window shows, including what only the GPU draws (like widgets clipped to their
     * rounded corners). Falls back to drawing the views in software if the copy fails, comes back
     * blank or takes too long.
     */
    private fun capture(activity: Activity, done: (bitmap: () -> Bitmap) -> Unit) {
        val window = activity.window
        val view = window.decorView
        val main = Handler(Looper.getMainLooper())
        var finished = false
        fun finish(how: String, bitmap: () -> Bitmap) {
            if (finished) return
            finished = true
            Log.i(TAG, "Snapshot by $how")
            done(bitmap)
        }
        val software = Runnable { finish("software drawing") { view.drawToBitmap() } }
        if (view.width == 0 || view.height == 0) return software.run()
        val copy = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        main.postDelayed(software, 3_000)
        try {
            PixelCopy.request(window, copy, { result ->
                main.removeCallbacks(software)
                if (result == PixelCopy.SUCCESS && !copy.isBlank()) finish("pixel copy") { copy } else software.run()
            }, main)
        } catch (e: IllegalArgumentException) {
            main.removeCallbacks(software)
            software.run()
        }
    }

    private fun save(activity: Activity, name: String, bitmap: Bitmap): String {
        val directory = File(activity.filesDir, "snapshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "saved"
    }

    /** True when every sampled pixel is the same, as in a copy of a frame that was never drawn. */
    private fun Bitmap.isBlank(): Boolean {
        val first = getPixel(0, 0)
        for (y in 0 until height step 40) {
            for (x in 0 until width step 40) if (getPixel(x, y) != first) return false
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val TAG = "Nimbus/Snapshot"
    }
}
