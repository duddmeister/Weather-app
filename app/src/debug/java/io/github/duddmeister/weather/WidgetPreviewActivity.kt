package io.github.duddmeister.weather

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.lifecycle.lifecycleScope
import io.github.duddmeister.weather.ui.SkyKind
import io.github.duddmeister.weather.ui.WeatherVisuals
import io.github.duddmeister.weather.widget.ClockWidgetContent
import io.github.duddmeister.weather.widget.ForecastWidgetContent
import io.github.duddmeister.weather.widget.NowWidgetContent
import io.github.duddmeister.weather.widget.WidgetDay
import io.github.duddmeister.weather.widget.WidgetHour
import io.github.duddmeister.weather.widget.WidgetModel
import kotlinx.coroutines.launch

/** Debug-only gallery of the widget layouts at common sizes, rendered by Glance into real RemoteViews. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        val previews = listOf(
            Triple("Clock · 4×2", DpSize(330.dp, 180.dp), Kind.Clock),
            Triple("Clock · 4×1", DpSize(330.dp, 110.dp), Kind.Clock),
            Triple("Current · 2×2", DpSize(150.dp, 150.dp), Kind.Now),
            Triple("Current · 2×1", DpSize(170.dp, 72.dp), Kind.Now),
            Triple("Current · 4×1", DpSize(330.dp, 72.dp), Kind.Now),
            Triple("Forecast · 4×2", DpSize(330.dp, 180.dp), Kind.Forecast),
            Triple("Forecast · 4×4", DpSize(330.dp, 330.dp), Kind.Forecast),
        )
        lifecycleScope.launch {
            for ((label, size, kind) in previews) {
                column.addView(TextView(this@WidgetPreviewActivity).apply {
                    text = label
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(0, padding, 0, padding / 2)
                })
                val model = if (label.contains("4×4")) sample(refreshing = true) else sample(refreshing = false)
                try {
                    val result = GlanceRemoteViews().compose(this@WidgetPreviewActivity, size) {
                        when (kind) {
                            Kind.Clock -> ClockWidgetContent(model)
                            Kind.Now -> NowWidgetContent(model)
                            Kind.Forecast -> ForecastWidgetContent(model)
                        }
                    }
                    val view = result.remoteViews.apply(this@WidgetPreviewActivity, column)
                    column.addView(view, ViewGroup.LayoutParams((size.width.value * density).toInt(), (size.height.value * density).toInt()))
                } catch (e: Exception) {
                    column.addView(TextView(this@WidgetPreviewActivity).apply {
                        text = "Failed: $e"
                        setTextColor(0xFFFF8A80.toInt())
                    })
                }
            }
        }
    }

    private enum class Kind { Clock, Now, Forecast }

    private fun sample(refreshing: Boolean) = WidgetModel(
        locationId = "preview",
        locationName = "New York",
        isCurrentLocation = true,
        temperature = "18°",
        condition = "Partly cloudy",
        icon = R.drawable.wx_partly_cloudy_day,
        highLow = "H 21°  L 12°",
        feelsLike = "Feels like 17°",
        status = if (refreshing) "Updating…" else "Updated 3:42 PM",
        statusIsWarning = false,
        background = WeatherVisuals.widgetBackground(SkyKind.CloudyDay),
        hours = listOf(
            WidgetHour("Now", R.drawable.wx_partly_cloudy_day, "18°", null),
            WidgetHour("4 PM", R.drawable.wx_partly_cloudy_day, "19°", null),
            WidgetHour("5 PM", R.drawable.wx_cloudy, "18°", "20%"),
            WidgetHour("6 PM", R.drawable.wx_showers_day, "17°", "60%"),
            WidgetHour("7 PM", R.drawable.wx_rain, "15°", "80%"),
            WidgetHour("8 PM", R.drawable.wx_rain, "14°", "70%"),
            WidgetHour("9 PM", R.drawable.wx_partly_cloudy_night, "13°", null),
        ),
        days = listOf(
            WidgetDay("Today", R.drawable.wx_showers_day, "21°", "12°", "80%", 0.35f, 0.75f),
            WidgetDay("Thu", R.drawable.wx_clear_day, "24°", "13°", null, 0.4f, 0.95f),
            WidgetDay("Fri", R.drawable.wx_partly_cloudy_day, "22°", "14°", null, 0.45f, 0.85f),
            WidgetDay("Sat", R.drawable.wx_thunderstorm, "19°", "11°", "90%", 0.3f, 0.65f),
            WidgetDay("Sun", R.drawable.wx_snow, "9°", "4°", "60%", 0f, 0.3f),
        ),
        refreshing = refreshing,
        hasData = true,
    )
}
