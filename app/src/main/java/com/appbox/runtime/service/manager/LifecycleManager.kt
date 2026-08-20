package com.appbox.runtime.service.manager

import com.appbox.runtime.core.contract.AppRegistryContract
import com.appbox.runtime.core.contract.RemoteMonitorContract
import com.appbox.runtime.core.model.AppLifecycleState
import com.appbox.runtime.core.model.MonitorEventType
import com.appbox.runtime.core.model.RemoteMonitorEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LifecycleManager(
    private val appRegistry: AppRegistryContract,
    private val remoteMonitor: RemoteMonitorContract,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val heartbeats = mutableMapOf<String, Long>()

    private val heartbeatTimeoutMs = 60_000L
    private val checkIntervalMs = 15_000L

    fun start() {
        scope.launch {
            while (isActive) {
                checkHeartbeats()
                delay(checkIntervalMs)
            }
        }
    }

    fun recordHeartbeat(packageName: String) {
        heartbeats[packageName] = System.currentTimeMillis()
        scope.launch {
            appRegistry.updateAppState(packageName, AppLifecycleState.ACTIVE)
        }
    }

    fun onClientConnected(packageName: String) {
        recordHeartbeat(packageName)
        scope.launch {
            if (appRegistry.getApp(packageName) == null) {
                (appRegistry as? AppRegistry)?.registerFromPackage(packageName)
            }
        }
    }

    fun onClientDisconnected(packageName: String) {
        scope.launch {
            appRegistry.updateAppState(packageName, AppLifecycleState.SUSPENDED)
        }
    }

    suspend fun suspendApp(packageName: String): Result<Unit> =
        appRegistry.updateAppState(packageName, AppLifecycleState.SUSPENDED)

    suspend fun stopApp(packageName: String): Result<Unit> =
        appRegistry.updateAppState(packageName, AppLifecycleState.STOPPED)

    private suspend fun checkHeartbeats() {
        val now = System.currentTimeMillis()
        heartbeats.forEach { (packageName, lastBeat) ->
            if (now - lastBeat > heartbeatTimeoutMs) {
                appRegistry.updateAppState(packageName, AppLifecycleState.SUSPENDED)
                remoteMonitor.report(
                    RemoteMonitorEvent(
                        type = MonitorEventType.APP_STATE_CHANGED,
                        packageName = packageName,
                        message = "Heartbeat timeout — app suspended",
                    ),
                )
            }
        }
    }
}
