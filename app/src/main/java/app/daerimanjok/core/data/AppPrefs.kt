package app.daerimanjok.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "handover_prefs")

/**
 * Small on-device key/value store. No server is needed for the MVP, and data
 * never leaves the car (privacy rule). Use Room instead if a feature needs history/lists.
 */
class AppPrefs(context: Context) {

    private val store = context.applicationContext.dataStore

    fun string(key: String): Flow<String?> = store.data.map { it[stringPreferencesKey(key)] }

    suspend fun setString(key: String, value: String?) {
        store.edit { prefs ->
            val k = stringPreferencesKey(key)
            if (value == null) prefs.remove(k) else prefs[k] = value
        }
    }

    /** Wipe everything, e.g. for a "sign out / reset" button on a shared car. */
    suspend fun clearAll() {
        store.edit { it.clear() }
    }
}
