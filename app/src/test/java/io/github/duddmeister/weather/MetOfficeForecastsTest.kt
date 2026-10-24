package io.github.duddmeister.weather

import io.github.duddmeister.weather.core.HttpFetcher
import io.github.duddmeister.weather.data.MetOfficeAccount
import io.github.duddmeister.weather.data.MetOfficeAccountStore
import io.github.duddmeister.weather.data.MetOfficeForecasts
import io.github.duddmeister.weather.data.MetOfficeStatus
import io.github.duddmeister.weather.data.remote.MetOfficeClient
import io.github.duddmeister.weather.model.MetOfficeData
import io.github.duddmeister.weather.model.MetOfficeDay
import io.github.duddmeister.weather.model.MetOfficeHour
import io.github.duddmeister.weather.model.SavedLocation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Collections

class MetOfficeForecastsTest {
    private val server = MockWebServer()
    private val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    @Volatile private var responseCode = 200
    private var now = Instant.parse("2026-10-24T17:20:00Z").toEpochMilli()
    private val today = MetOfficeForecasts.utcDay(now)

    private val belfast = SavedLocation(id = "belfast", name = "Belfast", countryCode = "GB", latitude = 54.5973, longitude = -5.9301)
    private val paris = SavedLocation(id = "paris", name = "Paris", countryCode = "FR", latitude = 48.8566, longitude = 2.3522)

    private class MemoryStore(var account: MetOfficeAccount = MetOfficeAccount()) : MetOfficeAccountStore {
        override suspend fun load() = account
        override suspend fun save(account: MetOfficeAccount) {
            this.account = account
        }
    }

    @Before
    fun startServer() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val kind = request.requestUrl!!.pathSegments.last()
                requests += kind
                if (responseCode != 200) return MockResponse().setResponseCode(responseCode).setBody("""{"httpCode": "$responseCode"}""")
                return MockResponse().setBody(javaClass.getResource("/metoffice-$kind-sample.json")!!.readText())
            }
        }
        server.start()
    }

    @After
    fun stopServer() = server.shutdown()

    private fun TestScope.forecasts(store: MemoryStore) = MetOfficeForecasts(
        MetOfficeClient(HttpFetcher(OkHttpClient(), "test"), baseUrl = server.url("/sitespecific/v0/point/")),
        store,
        backgroundScope,
        clock = { now },
    )

    private fun withKey(calls: Int = 0) = MemoryStore(MetOfficeAccount(apiKey = "test-key", callDay = today, calls = calls))

    @Test
    fun nothingIsFetchedWithoutAKeyOrOutsideTheUkAndIreland() = runTest {
        assertNull(forecasts(MemoryStore()).forecastFor(belfast, null, requested = true))
        assertNull(forecasts(withKey()).forecastFor(paris, null, requested = true))
        assertEquals(emptyList<String>(), requests.toList())
    }

    @Test
    fun fetchesBothPartsThenOnlyWhatsDue() = runTest {
        val store = withKey()
        val forecasts = forecasts(store)
        val first = forecasts.forecastFor(belfast, null, requested = false)!!
        assertEquals(6, first.hourly.size)
        assertEquals(4, first.daily.size)
        assertEquals(now, first.hourlyFetchedAt)
        assertEquals(listOf("daily", "hourly"), requests.sorted())

        // Twenty minutes on, nothing is due.
        now += 20 * 60_000L
        assertEquals(first, forecasts.forecastFor(belfast, first, requested = false))
        assertEquals(2, requests.size)

        // After half an hour the hourly forecast is fetched again, but not the daily one.
        now += 15 * 60_000L
        val second = forecasts.forecastFor(belfast, first, requested = false)!!
        assertEquals(listOf("daily", "hourly", "hourly"), requests.sorted())
        assertEquals(now, second.hourlyFetchedAt)
        assertEquals(first.dailyFetchedAt, second.dailyFetchedAt)

        // Asking for a refresh shortens the wait to ten minutes.
        now += 11 * 60_000L
        forecasts.forecastFor(belfast, second, requested = true)
        assertEquals(4, requests.size)
        assertEquals(4, store.account.calls)
        assertEquals(MetOfficeStatus.Ready, forecasts.state.value.status)
        assertEquals(4, forecasts.state.value.callsToday)
    }

    @Test
    fun aNewKeyIsCheckedAndCountsStartAfresh() = runTest {
        val store = MemoryStore(MetOfficeAccount(apiKey = "old-key", callDay = today, calls = 100, keyRejected = true))
        val forecasts = forecasts(store)
        forecasts.setKey("  new-key\n")
        assertEquals("new-key", store.account.apiKey)
        assertEquals(1, store.account.calls)
        assertEquals(listOf("daily"), requests.toList())
        val state = forecasts.state.value
        assertEquals(MetOfficeStatus.Ready, state.status)
        assertEquals("-key", state.keyHint)
        assertEquals(now, state.lastSuccessAt)

        forecasts.setKey(null)
        assertNull(store.account.apiKey)
        assertEquals(MetOfficeStatus.NoKey, forecasts.state.value.status)
        assertNull(forecasts.forecastFor(belfast, null, requested = true))
        assertEquals(1, requests.size)
    }

    @Test
    fun aRejectedKeyStopsCalls() = runTest {
        responseCode = 401
        val store = MemoryStore()
        val forecasts = forecasts(store)
        forecasts.setKey("wrong-key")
        assertEquals(MetOfficeStatus.Rejected, forecasts.state.value.status)
        assertTrue(store.account.keyRejected)
        assertNull(forecasts.forecastFor(belfast, null, requested = true))
        assertEquals(1, requests.size)
    }

    @Test
    fun theDailyAllowanceIsKept() = runTest {
        val forecasts = forecasts(withKey(calls = MetOfficeForecasts.DAILY_CALL_LIMIT))
        assertNull(forecasts.forecastFor(belfast, null, requested = true))
        assertEquals(0, requests.size)
        assertEquals(MetOfficeStatus.LimitReached, forecasts.state.value.status)

        // The count starts again on the next UTC day.
        now = Instant.parse("2026-10-25T00:05:00Z").toEpochMilli()
        assertNotNull(forecasts.forecastFor(belfast, null, requested = false))
        assertEquals(2, requests.size)
        assertEquals(MetOfficeStatus.Ready, forecasts.state.value.status)
    }

    @Test
    fun beingToldTheAllowanceIsUsedUpPausesCallsUntilTheNextUtcDay() = runTest {
        responseCode = 429
        val store = withKey()
        val forecasts = forecasts(store)
        assertNull(forecasts.forecastFor(belfast, null, requested = true))
        val made = requests.size
        assertTrue("$made calls", made in 1..2)
        assertEquals(today, store.account.limitDay)
        assertEquals(MetOfficeStatus.LimitReached, forecasts.state.value.status)
        assertNull(forecasts.forecastFor(belfast, null, requested = true))
        assertEquals(made, requests.size)

        responseCode = 200
        now = Instant.parse("2026-10-25T00:05:00Z").toEpochMilli()
        assertNotNull(forecasts.forecastFor(belfast, null, requested = false))
        assertEquals(made + 2, requests.size)
    }

    @Test
    fun failedCallsKeepWhatWasFetchedBefore() = runTest {
        val forecasts = forecasts(withKey())
        val first = forecasts.forecastFor(belfast, null, requested = false)!!
        responseCode = 503
        now += 40 * 60_000L
        assertEquals(first, forecasts.forecastFor(belfast, first, requested = false))
        assertEquals(MetOfficeStatus.Unavailable, forecasts.state.value.status)
    }

    @Test
    fun staleForecastsAreDropped() {
        val data = MetOfficeData(
            hourly = listOf(MetOfficeHour(time = 0)),
            daily = listOf(MetOfficeDay(date = "2026-10-24")),
            hourlyFetchedAt = now - 7 * 3600_000L,
            dailyFetchedAt = now - 11 * 3600_000L,
        )
        val usable = MetOfficeForecasts.usable(data, now)
        assertEquals(emptyList<MetOfficeHour>(), usable.hourly)
        assertEquals(data.daily, usable.daily)
        assertTrue(MetOfficeForecasts.hourlyDue(usable, now, requested = false))
    }

    @Test
    fun howOftenEachPartIsFetched() {
        fun fetched(minutesAgo: Int) = MetOfficeData(
            hourly = listOf(MetOfficeHour(time = 0)),
            daily = listOf(MetOfficeDay(date = "2026-10-24")),
            hourlyFetchedAt = now - minutesAgo * 60_000L,
            dailyFetchedAt = now - minutesAgo * 60_000L,
        )
        assertFalse(MetOfficeForecasts.hourlyDue(fetched(29), now, requested = false))
        assertTrue(MetOfficeForecasts.hourlyDue(fetched(30), now, requested = false))
        assertFalse(MetOfficeForecasts.hourlyDue(fetched(9), now, requested = true))
        assertTrue(MetOfficeForecasts.hourlyDue(fetched(10), now, requested = true))
        assertFalse(MetOfficeForecasts.dailyDue(fetched(179), now, requested = false))
        assertTrue(MetOfficeForecasts.dailyDue(fetched(180), now, requested = false))
        assertFalse(MetOfficeForecasts.dailyDue(fetched(59), now, requested = true))
        assertTrue(MetOfficeForecasts.dailyDue(fetched(60), now, requested = true))
        assertTrue(MetOfficeForecasts.dailyDue(MetOfficeData(), now, requested = false))
    }

    @Test
    fun keysAreCleanedUp() {
        assertEquals("abcd", MetOfficeForecasts.cleanKey(" ab\ncd "))
        assertEquals("abc", MetOfficeForecasts.cleanKey("\"abc\""))
        assertNull(MetOfficeForecasts.cleanKey(" \n "))
    }
}
