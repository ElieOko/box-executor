package com.appbox.runtime.service.overlay

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.overlayPositions: DataStore<Preferences> by preferencesDataStore(name = "overlay_positions")

data class OverlayPosition(val x: Int, val y: Int)

class OverlayPositionStore(private val context: Context) {

    private object Keys {
        fun x(key: String) = intPreferencesKey("${key}_x")
        fun y(key: String) = intPreferencesKey("${key}_y")
    }

    fun getPosition(key: String, defaultX: Int, defaultY: Int): OverlayPosition = runBlocking {
        context.overlayPositions.data.map { prefs ->
            OverlayPosition(
                x = prefs[Keys.x(key)] ?: defaultX,
                y = prefs[Keys.y(key)] ?: defaultY,
            )
        }.first()
    }

    suspend fun savePosition(key: String, x: Int, y: Int) {
        context.overlayPositions.edit { prefs ->
            prefs[Keys.x(key)] = x
            prefs[Keys.y(key)] = y
        }
    }
}
