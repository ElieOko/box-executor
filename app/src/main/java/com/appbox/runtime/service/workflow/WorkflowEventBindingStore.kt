package com.appbox.runtime.service.workflow

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.appbox.runtime.core.model.WorkflowEventBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.workflowEventBindingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "workflow_event_bindings",
)

class WorkflowEventBindingStore(context: Context) {

    private val dataStore = context.workflowEventBindingsStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("bindings")

    val bindingsFlow: Flow<List<WorkflowEventBinding>> = dataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<WorkflowEventBinding>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun load(): List<WorkflowEventBinding> {
        val raw = dataStore.data.map { it[key] }.first()
        return if (raw == null) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<WorkflowEventBinding>>(raw) }.getOrDefault(emptyList())
        }
    }

    suspend fun save(bindings: List<WorkflowEventBinding>) {
        dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(bindings)
        }
    }

    suspend fun upsert(binding: WorkflowEventBinding) {
        val current = load().toMutableList()
        val index = current.indexOfFirst { it.id == binding.id || it.topic == binding.topic }
        if (index >= 0) current[index] = binding else current += binding
        save(current)
    }

    suspend fun remove(id: String) {
        save(load().filter { it.id != id })
    }
}
