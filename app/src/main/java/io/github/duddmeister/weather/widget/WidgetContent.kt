package io.github.duddmeister.weather.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.duddmeister.weather.MainActivity
import io.github.duddmeister.weather.R

private val White = ColorProvider(Color.White)
private val WhiteSecondary = ColorProvider(Color.White.copy(alpha = 0.82f))
private val WhiteTertiary = ColorProvider(Color.White.copy(alpha = 0.66f))
private val Warning = ColorProvider(Color(0xFFFFD27A))
private val RainBlue = ColorProvider(Color(0xFF9AD8FF))
private val Light = FontFamily("sans-serif-light")

private fun text(size: TextUnit, color: ColorProvider = White, weight: FontWeight = FontWeight.Normal, family: FontFamily? = null, align: TextAlign? = null) =
    TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = family, textAlign = align)

@Composable
private fun WidgetFrame(model: WidgetModel, horizontal: Dp = 14.dp, vertical: Dp = 12.dp, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val open = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        model.locationId?.let { putExtra(MainActivity.EXTRA_LOCATION_ID, it) }
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(model.background))
            .cornerRadius(24.dp)
            .clickable(actionStartActivity(open))
            .padding(horizontal = horizontal, vertical = vertical),
    ) {
        content()
    }
}

@Composable
private fun RefreshButton(model: WidgetModel, size: Dp = 30.dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(ImageProvider(R.drawable.widget_button_bg))
            .clickable(actionRunCallback<RefreshAction>()),
        contentAlignment = Alignment.Center,
    ) {
        if (model.refreshing) {
            CircularProgressIndicator(modifier = GlanceModifier.size(size * 0.55f), color = White)
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = "Refresh weather",
                modifier = GlanceModifier.size(size * 0.6f),
            )
        }
    }
}

@Composable
private fun LocationLabel(model: WidgetModel, size: TextUnit = 13.sp, modifier: GlanceModifier = GlanceModifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (model.isCurrentLocation) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_location),
                contentDescription = "Current location",
                modifier = GlanceModifier.size(11.dp),
            )
            Spacer(GlanceModifier.width(4.dp))
        }
        Text(text = model.locationName, style = text(size, weight = FontWeight.Medium), maxLines = 1)
    }
}

@Composable
private fun StatusLine(model: WidgetModel, size: TextUnit = 11.sp) {
    Text(text = model.status, style = text(size, if (model.statusIsWarning) Warning else WhiteTertiary), maxLines = 1)
}

@Composable
private fun WeatherIcon(icon: Int, size: Dp, description: String) {
    Image(
        provider = ImageProvider(icon),
        contentDescription = description,
        modifier = GlanceModifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

// ---------------------------------------------------------------- Current weather widget

@Composable
fun NowWidgetContent(model: WidgetModel) {
    val size = LocalSize.current
    when {
        size.height < 100.dp && size.width >= 200.dp -> WidgetFrame(model, vertical = 8.dp) { NowWide(model) }
        size.height < 100.dp -> WidgetFrame(model, horizontal = 10.dp, vertical = 8.dp) { NowCompact(model) }
        else -> WidgetFrame(model) { NowTall(model, wide = size.width >= 200.dp) }
    }
}

@Composable
private fun NowCompact(model: WidgetModel) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(model.icon, 34.dp, model.condition)
        Spacer(GlanceModifier.width(6.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(text = model.temperature, style = text(26.sp, family = Light), maxLines = 1)
            StatusLine(model, 10.sp)
        }
        RefreshButton(model, 28.dp)
    }
}

@Composable
private fun NowWide(model: WidgetModel) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(model.icon, 40.dp, model.condition)
        Spacer(GlanceModifier.width(8.dp))
        Text(text = model.temperature, style = text(32.sp, family = Light), maxLines = 1)
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            LocationLabel(model, 13.sp)
            Text(text = listOf(model.condition, model.highLow).filter { it.isNotBlank() }.joinToString("  ·  "), style = text(11.sp, WhiteSecondary), maxLines = 1)
            StatusLine(model, 10.sp)
        }
        RefreshButton(model)
    }
}

@Composable
private fun NowTall(model: WidgetModel, wide: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LocationLabel(model, modifier = GlanceModifier.defaultWeight())
            RefreshButton(model)
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = model.temperature, style = text(if (wide) 52.sp else 44.sp, family = Light), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            WeatherIcon(model.icon, if (wide) 54.dp else 44.dp, model.condition)
        }
        Text(text = model.condition, style = text(13.sp, weight = FontWeight.Medium), maxLines = 1)
        if (model.highLow.isNotBlank()) {
            Text(
                text = if (wide && model.feelsLike != null) "${model.highLow}  ·  ${model.feelsLike}" else model.highLow,
                style = text(11.sp, WhiteSecondary),
                maxLines = 1,
            )
        }
        StatusLine(model, 10.sp)
    }
}

// ---------------------------------------------------------------- Forecast widget

@Composable
fun ForecastWidgetContent(model: WidgetModel) {
    val size = LocalSize.current
    WidgetFrame(model) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            ForecastHeader(model)
            if (model.hasData) {
                Spacer(GlanceModifier.height(10.dp))
                val hourCount = when {
                    size.width >= 320.dp -> 7
                    size.width >= 250.dp -> 6
                    else -> 4
                }
                HourlyRow(model.hours.take(hourCount))
                if (size.height >= 200.dp) {
                    Spacer(GlanceModifier.height(10.dp))
                    val dayCount = when {
                        size.height >= 280.dp -> 5
                        else -> 3
                    }
                    DailyRows(model.days.take(dayCount), rangeWidth = if (size.width >= 320.dp) 90.dp else 60.dp)
                }
            }
        }
    }
}

@Composable
private fun ForecastHeader(model: WidgetModel) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = model.temperature, style = text(42.sp, family = Light), maxLines = 1)
        Spacer(GlanceModifier.width(6.dp))
        WeatherIcon(model.icon, 40.dp, model.condition)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            LocationLabel(model)
            Text(text = model.condition, style = text(12.sp, WhiteSecondary), maxLines = 1)
            Text(text = model.highLow.ifBlank { model.status }, style = text(11.sp, WhiteSecondary), maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            RefreshButton(model)
            if (model.highLow.isNotBlank()) {
                Spacer(GlanceModifier.height(4.dp))
                Text(text = model.status.removePrefix("Updated "), style = text(10.sp, if (model.statusIsWarning) Warning else WhiteTertiary), maxLines = 1)
            }
        }
    }
}

@Composable
private fun HourlyRow(hours: List<WidgetHour>) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        hours.forEach { hour ->
            Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = hour.label, style = text(11.sp, WhiteSecondary, align = TextAlign.Center), maxLines = 1)
                Spacer(GlanceModifier.height(2.dp))
                WeatherIcon(hour.icon, 26.dp, hour.label)
                Spacer(GlanceModifier.height(2.dp))
                Text(text = hour.temperature, style = text(13.sp, weight = FontWeight.Medium, align = TextAlign.Center), maxLines = 1)
                Text(text = hour.precipitation ?: " ", style = text(9.sp, RainBlue, align = TextAlign.Center), maxLines = 1)
            }
        }
    }
}

@Composable
private fun DailyRows(days: List<WidgetDay>, rangeWidth: Dp) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        days.forEach { day ->
            Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = day.label, style = text(12.sp, weight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.width(48.dp))
                WeatherIcon(day.icon, 22.dp, day.label)
                Text(text = day.precipitation ?: "", style = text(10.sp, RainBlue), maxLines = 1, modifier = GlanceModifier.width(36.dp).padding(start = 4.dp))
                Spacer(GlanceModifier.defaultWeight())
                Text(text = day.low, style = text(12.sp, WhiteTertiary, align = TextAlign.End), maxLines = 1, modifier = GlanceModifier.width(34.dp))
                Spacer(GlanceModifier.width(6.dp))
                RangeBar(day.rangeStart, day.rangeEnd, rangeWidth)
                Spacer(GlanceModifier.width(6.dp))
                Text(text = day.high, style = text(12.sp, weight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.width(34.dp))
            }
        }
    }
}

@Composable
private fun RangeBar(start: Float, end: Float, width: Dp) {
    Box(modifier = GlanceModifier.width(width).height(5.dp).background(ImageProvider(R.drawable.widget_track))) {
        Row(modifier = GlanceModifier.fillMaxSize()) {
            Spacer(GlanceModifier.width(width * start))
            Box(
                modifier = GlanceModifier
                    .width(max(width * (end - start), 5.dp))
                    .fillMaxHeight()
                    .background(ImageProvider(R.drawable.widget_range)),
            ) {}
        }
    }
}
