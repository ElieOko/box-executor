package com.appbox.runtime.service.scheduler

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.appbox.runtime.core.contract.SchedulerServiceContract
import com.appbox.runtime.core.model.ScheduledTask
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.scheduleDataStore: DataStore<Preferences> by preferencesDataStore(name = "appbox_schedules")

class SchedulerService(
    private val context: Context,
    private val onScheduleFire: suspend (ScheduledTask) -> Unit,
) : SchedulerServiceContract {

    private val dataStore = context.applicationContext.scheduleDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val tasksKey = stringPreferencesKey("tasks")
    private val mutex = Mutex()
    private val tasks = mutableMapOf<String, ScheduledTask>()

    suspend fun initialize() {
        loadTasks().forEach { tasks[it.id] = it }
        rescheduleAll()
    }

    override suspend fun register(task: ScheduledTask): Result<Unit> = mutex.withLock {
        runCatching {
            val nextRun = ScheduleAlarmHelper.computeNextRun(task)
            val updated = task.copy(nextRunAt = nextRun)
            tasks[task.id] = updated
            persist()
            ScheduleAlarmHelper.schedule(context, updated)
        }
    }

    override suspend fun cancel(taskId: String): Result<Unit> = mutex.withLock {
        runCatching {
            tasks.remove(taskId)
            persist()
            ScheduleAlarmHelper.cancel(context, taskId)
        }
    }

    override suspend fun getAll(): List<ScheduledTask> = tasks.values.toList()

    override suspend fun rescheduleAll(): Result<Unit> = mutex.withLock {
        runCatching {
            tasks.values.forEach { ScheduleAlarmHelper.schedule(context, it) }
        }
    }

    suspend fun markRun(taskId: String) {
        mutex.withLock {
            val task = tasks[taskId] ?: return
            val now = System.currentTimeMillis()
            val updated = task.copy(
                lastRunAt = now,
                nextRunAt = ScheduleAlarmHelper.computeNextRun(
                    task.copy(lastRunAt = now),
                ),
            )
            tasks[taskId] = updated
            persist()
            if (updated.triggerType == com.appbox.runtime.core.model.ScheduleTriggerType.DAILY_AT ||
                updated.triggerType == com.appbox.runtime.core.model.ScheduleTriggerType.INTERVAL
            ) {
                ScheduleAlarmHelper.schedule(context, updated)
            }
        }
    }

    private suspend fun loadTasks(): List<ScheduledTask> {
        val raw = dataStore.data.map { it[tasksKey] }.first() ?: return emptyList()
        return runCatching { json.decodeFromString<List<ScheduledTask>>(raw) }.getOrDefault(emptyList())
    }

    private suspend fun persist() {
        dataStore.edit { prefs ->
            prefs[tasksKey] = json.encodeToString(tasks.values.toList())
        }
    }
}
