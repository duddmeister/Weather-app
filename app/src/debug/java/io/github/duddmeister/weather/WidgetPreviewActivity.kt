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
import io.github.duddmeister.weather.domain.conditionsAt
import io.github.duddmeister.weather.model.CurrentConditions
import io.github.duddmeister.weather.model.WeatherSnapshot
import io.github.duddmeister.weather.ui.SkyKind
import io.github.duddmeister.weather.ui.WeatherVisuals
import io.github.duddmeister.weather.ui.sky.SkyScenes
import io.github.duddmeister.weather.widget.ClockWidgetContent
import io.github.duddmeister.weather.widget.ForecastWidgetContent
import io.github.duddmeister.weather.widget.NowWidgetContent
import io.github.duddmeister.weather.widget.WidgetDay
import io.github.duddmeister.weather.widget.WidgetHour
import io.github.duddmeister.weather.widget.WidgetModel
import io.github.duddmeister.weather.widget.WidgetSky
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

        // Each layout under a different sky, so one pass shows every effect at a real size, with clouds
        // in each of their shapes (wide, standard and square).
        val previews = listOf(
            Preview("Clock (slim) · 4×1 · cloudy night", DpSize(298.dp, 82.dp), Kind.Clock, code = 3, isDay = false),
            Preview("Clock (slim) · short row · partly cloudy", DpSize(330.dp, 60.dp), Kind.Clock, code = 2, isDay = true),
            Preview("Clock · 4×2 (large display size) · partly cloudy night", DpSize(298.dp, 171.dp), Kind.Clock, code = 2, isDay = false),
            Preview("Clock · 4×2 · thunderstorm", DpSize(340.dp, 205.dp), Kind.Clock, code = 95, isDay = true),
            Preview("Clock · 4×1 · snow", DpSize(330.dp, 110.dp), Kind.Clock, code = 73, isDay = true),
            Preview("Current · 2×2 · partly cloudy", DpSize(150.dp, 150.dp), Kind.Now, code = 2, isDay = true),
            Preview("Current · 2×1 · fog", DpSize(170.dp, 72.dp), Kind.Now, code = 45, isDay = true),
            Preview("Current · 4×1 · heavy rain at night", DpSize(330.dp, 72.dp), Kind.Now, code = 65, isDay = false),
            Preview("Forecast · 4×2 · clear night", DpSize(330.dp, 180.dp), Kind.Forecast, code = 0, isDay = false),
            Preview("Forecast · 4×4 · cloudy, updating", DpSize(330.dp, 330.dp), Kind.Forecast, code = 3, isDay = true, refreshing = true),
        )
        lifecycleScope.launch {
            for (preview in previews) {
                val size = preview.size
                column.addView(TextView(this@WidgetPreviewActivity).apply {
                    text = preview.label
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(0, padding, 0, padding / 2)
                })
                val model = sample(preview)
                try {
                    val result = GlanceRemoteViews().compose(this@WidgetPreviewActivity, size) {
                        when (preview.kind) {
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

    /** A layout at [size] showing WMO weather [code], by day or night. */
    private data class Preview(val label: String, val size: DpSize, val kind: Kind, val code: Int, val isDay: Boolean, val refreshing: Boolean = false)

    private fun sample(preview: Preview): WidgetModel {
        // The sky comes from the same scene the app and the real widgets use.
        val nowMillis = System.currentTimeMillis()
        val snapshot = WeatherSnapshot(
            locationId = "preview",
            latitude = 40.71,
            longitude = -74.01,
            timezone = "UTC",
            fetchedAt = nowMillis,
            current = CurrentConditions(time = nowMillis / 1000, temperature = 18.0, weatherCode = preview.code, isDay = preview.isDay),
        )
        val now = snapshot.conditionsAt(nowMillis)
        val scene = SkyScenes.forConditions(snapshot, now, nowMillis)
        return sample(preview.refreshing).copy(
            condition = now.condition.label(now.isDay),
            icon = WeatherVisuals.icon(now.weatherCode, now.isDay),
            background = WeatherVisuals.widgetBackground(scene.kind),
            sky = WidgetSky.of(scene),
        )
    }

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
