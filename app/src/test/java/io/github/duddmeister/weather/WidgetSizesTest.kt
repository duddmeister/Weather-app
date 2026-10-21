package io.github.duddmeister.weather

import androidx.glance.appwidget.SizeMode
import io.github.duddmeister.weather.widget.ClockWidget
import io.github.duddmeister.weather.widget.ForecastWidget
import io.github.duddmeister.weather.widget.MAX_SIZES
import io.github.duddmeister.weather.widget.NowWidget
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSizesTest {
    @Test
    fun everyWidgetDeclaresNoMoreSizesThanAndroidAccepts() {
        for (widget in listOf(NowWidget(), ForecastWidget(), ClockWidget())) {
            val sizes = (widget.sizeMode as SizeMode.Responsive).sizes
            assertTrue("${widget::class.simpleName} declares ${sizes.size} sizes", sizes.size in 1..MAX_SIZES)
        }
    }
}
