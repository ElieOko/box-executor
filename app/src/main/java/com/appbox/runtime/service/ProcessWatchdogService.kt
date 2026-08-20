package com.appbox.runtime.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.appbox.runtime.AppBoxRuntimeApplication
import com.appbox.runtime.container.LockTaskManager
import com.appbox.runtime.container.ProcessTracker
import com.appbox.runtime.core.model.TrackedProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ProcessWatchdogService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var processTracker: ProcessTracker

    override fun onCreate() {
        super.onCreate()
        processTracker = ProcessTracker(this)
        startForeground(NOTIFICATION_ID, buildNotification(emptyList()))
        scope.launch { monitorLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun monitorLoop() {
        val container = (application as AppBoxRuntimeApplication).container
        while (scope.isActive) {
            val apps = container.appRegistry.getAllApps()
            val registered = apps.associate { it.packageName to it.displayName }
            val tracked = processTracker.scanRegisteredProcesses(registered)
            _processes.value = tracked
            updateNotification(tracked)

            val allowedPackages = listOf(packageName) + apps.map { it.packageName }
            LockTaskManager.syncWhitelist(this@ProcessWatchdogService, apps.map { it.packageName })
            ReturnOverlayService.update(this@ProcessWatchdogService, allowedPackages)

            delay(POLL_INTERVAL_MS)
        }
    }

    private fun updateNotification(processes: List<TrackedProcess>) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(processes))
    }

    private fun buildNotification(processes: List<TrackedProcess>): Notification {
        val active = processes.count { it.isForeground }
        return NotificationCompat.Builder(this, AppBoxRuntimeApplication.RUNTIME_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("AppBox Runtime actif")
            .setContentText("$active processus encadrés · ${processes.size} suivis")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val POLL_INTERVAL_MS = 1500L

        private val _processes = MutableStateFlow<List<TrackedProcess>>(emptyList())
        val processes: StateFlow<List<TrackedProcess>> = _processes.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, ProcessWatchdogService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProcessWatchdogService::class.java))
        }
    }
}
