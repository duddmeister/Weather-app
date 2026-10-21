package io.github.duddmeister.weather.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import io.github.duddmeister.weather.container
import io.github.duddmeister.weather.core.AppLog
import io.github.duddmeister.weather.work.WorkScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object WidgetKeys {
    /** Per-widget location; absent means "the first place in the list". */
    val LOCATION_ID = stringPreferencesKey("location_id")
}

/** Shared plumbing: both widgets render from the same live inputs. */
abstract class NimbusWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.container
        container.repository.awaitLoaded()
        val inputs = widgetInputs(container)
        val initial = inputs.first()
        provideContent {
            val current by inputs.collectAsState(initial)
            val locationId = currentState<Preferences>()[WidgetKeys.LOCATION_ID]
            val context = LocalContext.current
            val model = WidgetModels.build(current, locationId, System.currentTimeMillis(), WidgetModels.use24h(context, current.settings))
            Content(model)
        }
    }

    @Composable
    abstract fun Content(model: WidgetModel)
}

class NowWidget : NimbusWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(100.dp, 50.dp), DpSize(200.dp, 50.dp),
            DpSize(110.dp, 110.dp), DpSize(200.dp, 110.dp),
            DpSize(110.dp, 180.dp), DpSize(200.dp, 180.dp),
        ),
    )

    @Composable
    override fun Content(model: WidgetModel) = NowWidgetContent(model)
}

class ForecastWidget : NimbusWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        listOf(180.dp, 250.dp, 320.dp).flatMap { width ->
            listOf(110.dp, 130.dp, 175.dp, 245.dp, 270.dp, 300.dp).map { height -> DpSize(width, height) }
        }.toSet(),
    )

    @Composable
    override fun Content(model: WidgetModel) = ForecastWidgetContent(model)
}

class ClockWidget : NimbusWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        listOf(180.dp, 250.dp, 300.dp).flatMap { width ->
            listOf(100.dp, 135.dp, 180.dp).map { height -> DpSize(width, height) }
        }.toSet(),
    )

    @Composable
    override fun Content(model: WidgetModel) = ClockWidgetContent(model)
}

class NowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdater.refreshIfStale(context)
    }
}

class ForecastWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ForecastWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdater.refreshIfStale(context)
    }
}

class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdater.refreshIfStale(context)
    }
}

/** The widget reload button. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.container.refresher.refreshFromWidget()
    }
}

object WidgetUpdater {
    private const val TAG = "WidgetUpdater"
    private const val STALE_MILLIS = 20 * 60 * 1000L

    private fun allWidgets(): List<NimbusWidget> = listOf(NowWidget(), ForecastWidget(), ClockWidget())

    /** The widget behind an AppWidget provider (receiver) class. */
    fun widgetFor(providerClassName: String?): NimbusWidget = when (providerClassName) {
        ForecastWidgetReceiver::class.java.name -> ForecastWidget()
        ClockWidgetReceiver::class.java.name -> ClockWidget()
        else -> NowWidget()
    }

    suspend fun updateAll(context: Context) {
        for (widget in allWidgets()) {
            try {
                widget.updateAll(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Widget update failed", e)
            }
        }
    }

    /** Locations explicitly chosen for widgets (widgets on the default follow the first place). */
    suspend fun locationIdsInUse(context: Context): Set<String> {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(NowWidget::class.java) +
            manager.getGlanceIds(ForecastWidget::class.java) +
            manager.getGlanceIds(ClockWidget::class.java)
        return ids.mapNotNull { id ->
            runCatching { getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[WidgetKeys.LOCATION_ID] }.getOrNull()
        }.toSet()
    }

    /** The launcher asked for an update (every 30 min or after a reboot): fetch if anything is old. */
    fun refreshIfStale(context: Context) {
        val container = context.container
        container.appScope.launch {
            container.repository.awaitLoaded()
            val now = System.currentTimeMillis()
            val display = container.locations.displayLocationsNow()
            val wanted = locationIdsInUse(context) + listOfNotNull(display.firstOrNull()?.id)
            val stale = wanted.any { id ->
                val snapshot = container.repository.snapshot(id)
                snapshot == null || now - snapshot.fetchedAt > STALE_MILLIS
            }
            if (stale && display.isNotEmpty()) WorkScheduler.refreshSoon(context)
        }
    }
}
