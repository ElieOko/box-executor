package com.appbox.runtime.service.workflow

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.appbox.runtime.core.model.WorkflowDefinition
import com.appbox.runtime.core.model.WorkflowRun
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.workflowDataStore: DataStore<Preferences> by preferencesDataStore(name = "appbox_workflows")

class WorkflowStore(context: Context) {

    private val dataStore = context.workflowDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val workflowsKey = stringPreferencesKey("definitions")
    private val runsKey = stringPreferencesKey("runs")

    suspend fun saveWorkflows(workflows: List<WorkflowDefinition>) {
        dataStore.edit { prefs ->
            prefs[workflowsKey] = json.encodeToString(workflows)
        }
    }

    suspend fun loadWorkflows(): List<WorkflowDefinition> {
        val raw = dataStore.data.map { it[workflowsKey] }.first() ?: return emptyList()
        return runCatching { json.decodeFromString<List<WorkflowDefinition>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun saveRuns(runs: List<WorkflowRun>) {
        dataStore.edit { prefs ->
            prefs[runsKey] = json.encodeToString(runs)
        }
    }

    suspend fun loadRuns(): List<WorkflowRun> {
        val raw = dataStore.data.map { it[runsKey] }.first() ?: return emptyList()
        return runCatching { json.decodeFromString<List<WorkflowRun>>(raw) }.getOrDefault(emptyList())
    }
}
