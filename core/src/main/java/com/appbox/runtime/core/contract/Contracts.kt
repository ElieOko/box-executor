package com.appbox.runtime.core.contract

import com.appbox.runtime.core.model.AppBoxApp
import com.appbox.runtime.core.model.AppBoxEvent
import com.appbox.runtime.core.model.AuthSession
import com.appbox.runtime.core.model.NetworkRequest
import com.appbox.runtime.core.model.NetworkResponse
import com.appbox.runtime.core.model.RuntimeNotification
import com.appbox.runtime.core.model.RuntimePermission
import com.appbox.runtime.core.model.StorageEntry

interface AppRegistryContract {
    suspend fun registerApp(app: AppBoxApp): Result<AppBoxApp>
    suspend fun unregisterApp(packageName: String): Result<Unit>
    suspend fun getApp(packageName: String): AppBoxApp?
    suspend fun getAllApps(): List<AppBoxApp>
    suspend fun updateAppState(packageName: String, state: com.appbox.runtime.core.model.AppLifecycleState): Result<Unit>
}

interface PermissionContract {
    suspend fun grant(packageName: String, permission: RuntimePermission): Result<Unit>
    suspend fun revoke(packageName: String, permission: RuntimePermission): Result<Unit>
    suspend fun hasPermission(packageName: String, permission: RuntimePermission): Boolean
    suspend fun getPermissions(packageName: String): Set<RuntimePermission>
}

interface AuthServiceContract {
    suspend fun authenticate(packageName: String, credentials: Map<String, String>): Result<AuthSession>
    suspend fun validateSession(sessionId: String): AuthSession?
    suspend fun revokeSession(sessionId: String): Result<Unit>
    suspend fun refreshSession(sessionId: String): Result<AuthSession>
}

interface StorageServiceContract {
    suspend fun put(entry: StorageEntry): Result<Unit>
    suspend fun get(namespace: String, key: String, requesterPackage: String): StorageEntry?
    suspend fun delete(namespace: String, key: String, requesterPackage: String): Result<Unit>
    suspend fun list(namespace: String, requesterPackage: String): List<StorageEntry>
}

interface NetworkServiceContract {
    suspend fun execute(request: NetworkRequest): Result<NetworkResponse>
    suspend fun isAllowed(packageName: String, url: String): Boolean
}

interface NotificationServiceContract {
    suspend fun post(notification: RuntimeNotification): Result<Unit>
    suspend fun getHistory(packageName: String, limit: Int = 50): List<RuntimeNotification>
}

interface EventBusContract {
    suspend fun publish(event: AppBoxEvent): Result<Unit>
    suspend fun subscribe(packageName: String, topics: Set<String>): Result<Unit>
    suspend fun unsubscribe(packageName: String, topics: Set<String>): Result<Unit>
}

interface RemoteMonitorContract {
    suspend fun report(event: com.appbox.runtime.core.model.RemoteMonitorEvent)
    fun isConnected(): Boolean
}
