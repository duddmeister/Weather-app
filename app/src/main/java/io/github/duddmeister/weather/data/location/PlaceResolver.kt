package io.github.duddmeister.weather.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import io.github.duddmeister.weather.core.AppLog
import io.github.duddmeister.weather.data.remote.PlaceName
import io.github.duddmeister.weather.data.remote.ReverseGeocodeClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Turns coordinates into a place name: the device Geocoder first, then a keyless web fallback. */
class PlaceResolver(
    context: Context,
    private val fallback: ReverseGeocodeClient,
) {
    private val context = context.applicationContext

    suspend fun resolve(latitude: Double, longitude: Double): PlaceName? {
        val fromDevice = try {
            geocode(latitude, longitude)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Geocoder failed", e)
            null
        }
        if (fromDevice != null) return fromDevice
        return try {
            fallback.reverse(latitude, longitude, Locale.getDefault().language)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Reverse geocoding fallback failed", e)
            null
        }
    }

    private suspend fun geocode(latitude: Double, longitude: Double): PlaceName? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        }
        address ?: return null
        val name = listOf(address.locality, address.subLocality, address.subAdminArea, address.adminArea)
            .firstOrNull { !it.isNullOrBlank() } ?: return null
        return PlaceName(
            name = name,
            region = address.adminArea?.takeIf { it.isNotBlank() && it != name },
            country = address.countryName,
            countryCode = address.countryCode,
        )
    }

    private companion object {
        const val TAG = "PlaceResolver"
    }
}
