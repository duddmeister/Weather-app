package io.github.duddmeister.weather.data.local

import android.content.Context
import io.github.duddmeister.weather.core.AppJson
import io.github.duddmeister.weather.core.AppLog
import io.github.duddmeister.weather.model.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One JSON file per location, so the app and widgets show the last forecast instantly and offline. */
class WeatherCache(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "weather")

    suspend fun loadAll(): Map<String, WeatherSnapshot> = withContext(Dispatchers.IO) {
        directory.mkdirs()
        directory.listFiles { file -> file.extension == "json" }.orEmpty().mapNotNull { file ->
            runCatching { AppJson.decodeFromString(WeatherSnapshot.serializer(), file.readText()) }
                .onFailure {
                    AppLog.w(TAG, "Dropping unreadable cache ${file.name}", it)
                    file.delete()
                }
                .getOrNull()
        }.associateBy { it.locationId }
    }

    suspend fun save(snapshot: WeatherSnapshot) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = fileFor(snapshot.locationId)
        val temp = File(directory, target.name + ".tmp")
        temp.writeText(AppJson.encodeToString(WeatherSnapshot.serializer(), snapshot))
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    suspend fun delete(locationId: String) = withContext(Dispatchers.IO) {
        fileFor(locationId).delete()
    }

    private fun fileFor(locationId: String) =
        File(directory, locationId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    private companion object {
        const val TAG = "WeatherCache"
    }
}
