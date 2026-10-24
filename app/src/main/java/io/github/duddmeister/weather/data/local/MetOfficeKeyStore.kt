package io.github.duddmeister.weather.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.duddmeister.weather.core.AppLog
import io.github.duddmeister.weather.data.MetOfficeAccount
import io.github.duddmeister.weather.data.MetOfficeAccountStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/** Its own file so backups can leave it out (see res/xml/backup_rules.xml). */
private val Context.secretsDataStore: DataStore<Preferences> by preferencesDataStore(name = "secrets")

/** Keeps the user's Met Office key, and today's call count, on this device only. */
class MetOfficeKeyStore(context: Context) : MetOfficeAccountStore {
    private val store = context.applicationContext.secretsDataStore

    private object Keys {
        val API_KEY = stringPreferencesKey("metoffice_api_key")
        val KEY_REJECTED = booleanPreferencesKey("metoffice_key_rejected")
        val CALL_DAY = longPreferencesKey("metoffice_call_day")
        val CALLS = intPreferencesKey("metoffice_calls")
        val LIMIT_DAY = longPreferencesKey("metoffice_limit_day")
        val LAST_SUCCESS = longPreferencesKey("metoffice_last_success")
    }

    override suspend fun load(): MetOfficeAccount {
        val prefs = store.data.catch { e ->
            if (e is IOException) {
                AppLog.w(TAG, "Met Office key unreadable", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }.first()
        return MetOfficeAccount(
            apiKey = prefs[Keys.API_KEY],
            keyRejected = prefs[Keys.KEY_REJECTED] ?: false,
            callDay = prefs[Keys.CALL_DAY] ?: 0L,
            calls = prefs[Keys.CALLS] ?: 0,
            limitDay = prefs[Keys.LIMIT_DAY],
            lastSuccessAt = prefs[Keys.LAST_SUCCESS],
        )
    }

    override suspend fun save(account: MetOfficeAccount) {
        store.edit { prefs ->
            if (account.apiKey != null) prefs[Keys.API_KEY] = account.apiKey else prefs.remove(Keys.API_KEY)
            prefs[Keys.KEY_REJECTED] = account.keyRejected
            prefs[Keys.CALL_DAY] = account.callDay
            prefs[Keys.CALLS] = account.calls
            if (account.limitDay != null) prefs[Keys.LIMIT_DAY] = account.limitDay else prefs.remove(Keys.LIMIT_DAY)
            if (account.lastSuccessAt != null) prefs[Keys.LAST_SUCCESS] = account.lastSuccessAt else prefs.remove(Keys.LAST_SUCCESS)
        }
    }

    private companion object {
        const val TAG = "MetOfficeKeyStore"
    }
}
