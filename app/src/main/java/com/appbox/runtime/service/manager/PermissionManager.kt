package com.appbox.runtime.service.manager

import com.appbox.runtime.core.contract.AppRegistryContract
import com.appbox.runtime.core.contract.PermissionContract
import com.appbox.runtime.core.contract.RemoteMonitorContract
import com.appbox.runtime.core.model.MonitorEventType
import com.appbox.runtime.core.model.RemoteMonitorEvent
import com.appbox.runtime.core.model.RuntimePermission
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PermissionManager(
    private val appRegistry: AppRegistryContract,
    private val remoteMonitor: RemoteMonitorContract,
) : PermissionContract {

    private val mutex = Mutex()
    private val overrides = mutableMapOf<String, MutableSet<RuntimePermission>>()

    override suspend fun grant(packageName: String, permission: RuntimePermission): Result<Unit> =
        mutex.withLock {
            runCatching {
                val app = appRegistry.getApp(packageName)
                    ?: throw IllegalArgumentException("App not registered: $packageName")
                val current = overrides.getOrPut(packageName) { app.permissions.toMutableSet() }
                current.add(permission)
                remoteMonitor.report(
                    RemoteMonitorEvent(
                        type = MonitorEventType.PERMISSION_CHANGED,
                        packageName = packageName,
                        message = "Granted $permission",
                    ),
                )
            }
        }

    override suspend fun revoke(packageName: String, permission: RuntimePermission): Result<Unit> =
        mutex.withLock {
            runCatching {
                val app = appRegistry.getApp(packageName)
                    ?: throw IllegalArgumentException("App not registered: $packageName")
                val current = overrides.getOrPut(packageName) { app.permissions.toMutableSet() }
                current.remove(permission)
                remoteMonitor.report(
                    RemoteMonitorEvent(
                        type = MonitorEventType.PERMISSION_CHANGED,
                        packageName = packageName,
                        message = "Revoked $permission",
                    ),
                )
            }
        }

    override suspend fun hasPermission(packageName: String, permission: RuntimePermission): Boolean {
        val app = appRegistry.getApp(packageName) ?: return false
        val granted = overrides[packageName] ?: app.permissions
        return granted.contains(permission)
    }

    override suspend fun getPermissions(packageName: String): Set<RuntimePermission> {
        val app = appRegistry.getApp(packageName) ?: return emptySet()
        return overrides[packageName] ?: app.permissions
    }
}
