package io.github.duddmeister.weather

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import io.github.duddmeister.weather.core.AppLog
import io.github.duddmeister.weather.widget.ClockWidgetReceiver
import io.github.duddmeister.weather.widget.ForecastWidgetReceiver
import io.github.duddmeister.weather.widget.NowWidgetReceiver

/**
 * Debug-only: hosts the real widgets the way a launcher does, so CI can watch them render live data
 * and press their refresh button. Binding needs `adb shell appwidget grantbind --package <package>`.
 * Start it again with [ACTION_PRESS_REFRESH] to tap every widget's refresh button.
 */
class WidgetHostActivity : ComponentActivity() {
    private lateinit var host: AppWidgetHost
    private val widgets = mutableListOf<AppWidgetHostView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = AppWidgetHost(this, HOST_ID)
        host.deleteHost() // Start from a clean slate each time.
        val manager = AppWidgetManager.getInstance(this)
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding * 3, padding, padding * 3)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF2B2F38.toInt())
            addView(column)
        })

        val specs = listOf(
            Triple("Clock & weather · live", ClockWidgetReceiver::class.java, SizeF(330f, 180f)),
            Triple("Current weather · live", NowWidgetReceiver::class.java, SizeF(150f, 150f)),
            Triple("Weather forecast · live", ForecastWidgetReceiver::class.java, SizeF(330f, 300f)),
        )
        for ((label, receiver, size) in specs) {
            column.addView(TextView(this).apply {
                text = label
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, padding, 0, padding / 2)
            })
            val id = host.allocateAppWidgetId()
            if (!manager.bindAppWidgetIdIfAllowed(id, ComponentName(this, receiver))) {
                column.addView(TextView(this).apply {
                    text = "Not allowed to bind widgets. Run: adb shell appwidget grantbind --package $packageName"
                    setTextColor(0xFFFF8A80.toInt())
                })
                continue
            }
            val view = host.createView(applicationContext, id, manager.getAppWidgetInfo(id))
            // Launchers report each widget's size so responsive layouts can pick the right one.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.updateAppWidgetSize(Bundle(), listOf(size))
            } else {
                @Suppress("DEPRECATION")
                view.updateAppWidgetSize(null, size.width.toInt(), size.height.toInt(), size.width.toInt(), size.height.toInt())
            }
            column.addView(view, ViewGroup.LayoutParams((size.width * density).toInt(), (size.height * density).toInt()))
            widgets += view
        }
        if (intent?.action == ACTION_PRESS_REFRESH) pressRefresh()
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        host.stopListening()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_PRESS_REFRESH) pressRefresh()
    }

    /** Clicks the view that owns each widget's "Refresh weather" button, as a finger would. */
    private fun pressRefresh() {
        for (widget in widgets) {
            val found = ArrayList<View>()
            widget.findViewsWithText(found, "Refresh weather", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
            var target: View? = found.firstOrNull()
            while (target != null && !target.hasOnClickListeners()) target = target.parent as? View
            if (target != null && target !== widget) {
                target.performClick()
                AppLog.i(TAG, "Pressed refresh on widget ${widget.appWidgetId}")
            } else {
                AppLog.w(TAG, "No refresh button on widget ${widget.appWidgetId}")
            }
        }
    }

    companion object {
        private const val TAG = "WidgetHost"
        private const val HOST_ID = 4711
        const val ACTION_PRESS_REFRESH = "io.github.duddmeister.weather.debug.PRESS_REFRESH"
    }
}
