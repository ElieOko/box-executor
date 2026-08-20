package com.appbox.runtime.service.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.appbox.runtime.core.model.AppBoxApp
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.appRegistryStore: DataStore<Preferences> by preferencesDataStore(
    name = "appbox_registry",
)

class AppRegistryStore(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.appRegistryStore

    suspend fun loadApps(): List<AppBoxApp> {
        val raw = dataStore.data.first()[APPS_KEY] ?: return emptyList()
        return runCatching { json.decodeFromString<List<AppBoxApp>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun saveApps(apps: List<AppBoxApp>) {
        dataStore.edit { prefs ->
            prefs[APPS_KEY] = json.encodeToString(apps)
        }
    }

    companion object {
        private val APPS_KEY = stringPreferencesKey("registered_apps")
    }
}
