package com.appbox.runtime.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.appbox.runtime.core.model.AppBoxEvent
import com.appbox.runtime.core.model.AuthSession
import com.appbox.runtime.core.model.NetworkRequest
import com.appbox.runtime.core.model.NetworkResponse
import com.appbox.runtime.core.model.RuntimeNotification
import com.appbox.runtime.core.model.RuntimePermission
import com.appbox.runtime.core.model.StorageEntry
import com.appbox.runtime.core.security.RuntimeConstants
import com.appbox.runtime.sdk.internal.RuntimeBinder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Point d'entrée principal pour les applications métier intégrées à AppBox Runtime.
 *
 * Usage:
 * ```
 * val client = AppBoxClient.connect(context)
 * client.auth.authenticate(mapOf("user" to "john", "password" to "secret"))
 * client.events.publish(AppBoxEvent(...))
 * ```
 */
class AppBoxClient private constructor(
    private val binder: RuntimeBinder,
    val packageName: String,
) {
    val auth: AuthApi = AuthApi(binder, packageName)
    val storage: StorageApi = StorageApi(binder, packageName)
    val network: NetworkApi = NetworkApi(binder, packageName)
    val notifications: NotificationApi = NotificationApi(binder, packageName)
    val events: EventApi = EventApi(binder, packageName)
    val permissions: PermissionApi = PermissionApi(binder, packageName)

    suspend fun heartbeat(): Boolean = binder.heartbeat(packageName)

    companion object {
        suspend fun connect(context: Context): Result<AppBoxClient> {
            val packageName = context.packageName
            return suspendCancellableCoroutine { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        if (service == null) {
                            continuation.resume(
                                Result.failure(IllegalStateException("Service binder is null")),
                            )
                            return
                        }
                        val binder = RuntimeBinder(service)
                        binder.registerClient(packageName)
                        continuation.resume(Result.success(AppBoxClient(binder, packageName)))
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        // Reconnection handled by caller
                    }
                }

                val intent = Intent(RuntimeConstants.RUNTIME_SERVICE_ACTION).apply {
                    setPackage(RuntimeConstants.RUNTIME_PACKAGE)
                }

                val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                if (!bound) {
                    continuation.resume(
                        Result.failure(
                            IllegalStateException("AppBox Runtime is not installed or not running"),
                        ),
                    )
                }

                continuation.invokeOnCancellation {
                    try {
                        context.unbindService(connection)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}

class AuthApi(
    private val binder: RuntimeBinder,
    private val packageName: String,
) {
    suspend fun authenticate(credentials: Map<String, String>): Result<AuthSession> =
        binder.authenticate(packageName, credentials)

    suspend fun validateSession(sessionId: String): AuthSession? =
        binder.validateSession(sessionId)

    suspend fun logout(sessionId: String): Result<Unit> =
        binder.revokeSession(sessionId)
}

class StorageApi(
    private val binder: RuntimeBinder,
    private val packageName: String,
) {
    suspend fun put(namespace: String, key: String, value: String, encrypted: Boolean = false): Result<Unit> {
        val entry = StorageEntry(
            key = key,
            namespace = namespace,
            ownerPackage = packageName,
            value = value,
            encrypted = encrypted,
        )
        return binder.storagePut(entry)
    }

    suspend fun get(namespace: String, key: String): StorageEntry? =
        binder.storageGet(namespace, key, packageName)

    suspend fun delete(namespace: String, key: String): Result<Unit> =
        binder.storageDelete(namespace, key, packageName)

    suspend fun list(namespace: String): List<StorageEntry> =
        binder.storageList(namespace, packageName)
}

class NetworkApi(
    private val binder: RuntimeBinder,
    private val packageName: String,
) {
    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): Result<NetworkResponse> {
        val request = NetworkRequest(
            id = "${packageName}_${System.currentTimeMillis()}",
            packageName = packageName,
            method = method,
            url = url,
            headers = headers,
            body = body,
        )
        return binder.networkExecute(request)
    }
}

class NotificationApi(
    private val binder: RuntimeBinder,
    private val packageName: String,
) {
    suspend fun post(title: String, body: String, channelId: String = "appbox_default"): Result<Unit> {
        val notification = RuntimeNotification(
            id = "${packageName}_${System.currentTimeMillis()}",
            sourcePackage = packageName,
            title = title,
            body = body,
            channelId = channelId,
        )
        return binder.notificationPost(notification)
    }
}

class EventApi(
    private val binder: RuntimeBinder,
    private val packageName: String,
) {
    suspend fun publish(event: AppBoxEvent): Result<Unit> =
        binder.eventPublish(event.copy(sourcePackage = packageName))

    suspend fun subscribe(topics: Set<String>): Result<Unit> =
        binder.eventSubscribe(packageName, topics)

    suspend fun unsubscribe(topics: Set<String>): Result<Unit> =
        binder.eventUnsubscribe(packageName, topics)

    fun observeEvents(): Flow<AppBoxEvent> = binder.observeEvents(packageName)
}

class PermissionApi(
    private val binder: RuntimeBinder,
    private val packageName: String,
) {
    suspend fun has(permission: RuntimePermission): Boolean =
        binder.hasPermission(packageName, permission)

    suspend fun list(): Set<RuntimePermission> =
        binder.getPermissions(packageName)
}
