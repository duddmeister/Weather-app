package io.github.duddmeister.weather.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.duddmeister.weather.core.AppLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Device location through the platform LocationManager (works with or without Google Play
 * services). City-level accuracy is plenty for weather, so a recent last-known fix is preferred.
 */
class LocationProvider(context: Context) {
    private val context = context.applicationContext

    fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)

    /** Whether location can be read while the app isn't in the foreground (widgets, background refresh). */
    fun hasBackgroundPermission(): Boolean = hasPermission() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION))

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    suspend fun currentLocation(timeoutMillis: Long = 12_000): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val lastKnown = lastKnown(manager)
        if (lastKnown != null && ageMillis(lastKnown) < FRESH_ENOUGH_MILLIS) return lastKnown

        for (provider in candidateProviders(manager)) {
            val fix = try {
                withTimeoutOrNull(timeoutMillis) { requestSingleFix(manager, provider) }
            } catch (e: SecurityException) {
                AppLog.w(TAG, "No permission for $provider", e)
                null
            } catch (e: IllegalArgumentException) {
                AppLog.w(TAG, "Provider $provider unavailable", e)
                null
            }
            if (fix != null) return fix
        }
        return lastKnown
    }

    private fun candidateProviders(manager: LocationManager): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.hasProvider(LocationManager.FUSED_PROVIDER)) {
            add(LocationManager.FUSED_PROVIDER)
        }
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }.filter { provider ->
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission") // Callers check hasPermission() first.
    private fun lastKnown(manager: LocationManager): Location? = runCatching {
        manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }.getOrNull()

    @SuppressLint("MissingPermission") // Callers check hasPermission() first.
    private suspend fun requestSingleFix(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) continuation.resume(location)
                    }

                    override fun onProviderDisabled(provider: String) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onProviderEnabled(provider: String) = Unit

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }

    private fun ageMillis(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    private companion object {
        const val TAG = "LocationProvider"
        const val FRESH_ENOUGH_MILLIS = 10 * 60 * 1000L
    }
}
