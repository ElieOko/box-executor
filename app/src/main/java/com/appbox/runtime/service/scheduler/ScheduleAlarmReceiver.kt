package com.appbox.runtime.service.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.appbox.runtime.core.model.ScheduleTriggerType
import com.appbox.runtime.core.model.ScheduledTask
import java.util.Calendar

class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val workflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID) ?: return
        val app = context.applicationContext as? com.appbox.runtime.AppBoxRuntimeApplication
            ?: return
        kotlinx.coroutines.runBlocking {
            app.container.automationAgent.onScheduleTriggered(taskId, workflowId)
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_WORKFLOW_ID = "workflow_id"
        const val ACTION_RUN_SCHEDULE = "com.appbox.runtime.SCHEDULE_RUN"
    }
}

object ScheduleAlarmHelper {

    fun schedule(context: Context, task: ScheduledTask) {
        if (!task.enabled) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = computeNextRun(task) ?: return

        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_RUN_SCHEDULE
            putExtra(ScheduleAlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(ScheduleAlarmReceiver.EXTRA_WORKFLOW_ID, task.workflowId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduleAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pending)
    }

    fun computeNextRun(task: ScheduledTask): Long? {
        val now = System.currentTimeMillis()
        return when (task.triggerType) {
            ScheduleTriggerType.ONE_SHOT -> {
                if (task.atEpochMs > now) task.atEpochMs else null
            }
            ScheduleTriggerType.DAILY_AT -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, task.hour)
                    set(Calendar.MINUTE, task.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
                }
                cal.timeInMillis
            }
            ScheduleTriggerType.INTERVAL -> {
                val base = task.lastRunAt ?: now
                base + task.intervalMs
            }
        }
    }
}
