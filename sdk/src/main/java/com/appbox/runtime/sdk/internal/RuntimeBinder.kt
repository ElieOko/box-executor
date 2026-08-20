package com.appbox.runtime.sdk.internal

import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import com.appbox.runtime.core.model.AppBoxEvent
import com.appbox.runtime.core.model.AuthSession
import com.appbox.runtime.core.model.NetworkRequest
import com.appbox.runtime.core.model.NetworkResponse
import com.appbox.runtime.core.model.RuntimeNotification
import com.appbox.runtime.core.model.RuntimePermission
import com.appbox.runtime.core.model.StorageEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Binder IPC bridge between SDK clients and the Runtime service.
 * Uses JSON serialization over Parcel for cross-process communication.
 */
class RuntimeBinder(private val remote: IBinder) {
    private val json = Json { ignoreUnknownKeys = true }

    fun registerClient(packageName: String) {
        transact(TRANSACTION_REGISTER, Bundle().apply {
            putString(KEY_PACKAGE, packageName)
        })
    }

    fun heartbeat(packageName: String): Boolean {
        val reply = transact(TRANSACTION_HEARTBEAT, Bundle().apply {
            putString(KEY_PACKAGE, packageName)
        })
        return reply?.getBoolean(KEY_RESULT) ?: false
    }

    fun authenticate(packageName: String, credentials: Map<String, String>): Result<AuthSession> =
        runCatching {
            val reply = transact(TRANSACTION_AUTH, Bundle().apply {
                putString(KEY_PACKAGE, packageName)
                putString(KEY_JSON, json.encodeToString(credentials))
            }) ?: throw IllegalStateException("No reply from runtime")
            val sessionJson = reply.getString(KEY_JSON)
                ?: throw IllegalStateException(reply.getString(KEY_ERROR) ?: "Auth failed")
            json.decodeFromString<AuthSession>(sessionJson)
        }

    fun validateSession(sessionId: String): AuthSession? {
        val reply = transact(TRANSACTION_VALIDATE_SESSION, Bundle().apply {
            putString(KEY_SESSION_ID, sessionId)
        }) ?: return null
        val sessionJson = reply.getString(KEY_JSON) ?: return null
        return json.decodeFromString<AuthSession>(sessionJson)
    }

    fun revokeSession(sessionId: String): Result<Unit> = runCatching {
        transact(TRANSACTION_REVOKE_SESSION, Bundle().apply {
            putString(KEY_SESSION_ID, sessionId)
        })
    }

    fun storagePut(entry: StorageEntry): Result<Unit> = runCatching {
        transact(TRANSACTION_STORAGE_PUT, Bundle().apply {
            putString(KEY_JSON, json.encodeToString(entry))
        })
    }

    fun storageGet(namespace: String, key: String, requester: String): StorageEntry? {
        val reply = transact(TRANSACTION_STORAGE_GET, Bundle().apply {
            putString(KEY_NAMESPACE, namespace)
            putString(KEY_KEY, key)
            putString(KEY_PACKAGE, requester)
        }) ?: return null
        val jsonStr = reply.getString(KEY_JSON) ?: return null
        return json.decodeFromString<StorageEntry>(jsonStr)
    }

    fun storageDelete(namespace: String, key: String, requester: String): Result<Unit> = runCatching {
        transact(TRANSACTION_STORAGE_DELETE, Bundle().apply {
            putString(KEY_NAMESPACE, namespace)
            putString(KEY_KEY, key)
            putString(KEY_PACKAGE, requester)
        })
    }

    fun storageList(namespace: String, requester: String): List<StorageEntry> {
        val reply = transact(TRANSACTION_STORAGE_LIST, Bundle().apply {
            putString(KEY_NAMESPACE, namespace)
            putString(KEY_PACKAGE, requester)
        }) ?: return emptyList()
        val jsonStr = reply.getString(KEY_JSON) ?: return emptyList()
        return json.decodeFromString(jsonStr)
    }

    fun networkExecute(request: NetworkRequest): Result<NetworkResponse> = runCatching {
        val reply = transact(TRANSACTION_NETWORK, Bundle().apply {
            putString(KEY_JSON, json.encodeToString(request))
        }) ?: throw IllegalStateException("No reply from runtime")
        val responseJson = reply.getString(KEY_JSON)
            ?: throw IllegalStateException(reply.getString(KEY_ERROR) ?: "Network failed")
        json.decodeFromString<NetworkResponse>(responseJson)
    }

    fun notificationPost(notification: RuntimeNotification): Result<Unit> = runCatching {
        transact(TRANSACTION_NOTIFICATION, Bundle().apply {
            putString(KEY_JSON, json.encodeToString(notification))
        })
    }

    fun eventPublish(event: AppBoxEvent): Result<Unit> = runCatching {
        transact(TRANSACTION_EVENT_PUBLISH, Bundle().apply {
            putString(KEY_JSON, json.encodeToString(event))
        })
    }

    fun eventSubscribe(packageName: String, topics: Set<String>): Result<Unit> = runCatching {
        transact(TRANSACTION_EVENT_SUBSCRIBE, Bundle().apply {
            putString(KEY_PACKAGE, packageName)
            putString(KEY_JSON, json.encodeToString(topics.toList()))
        })
    }

    fun eventUnsubscribe(packageName: String, topics: Set<String>): Result<Unit> = runCatching {
        transact(TRANSACTION_EVENT_UNSUBSCRIBE, Bundle().apply {
            putString(KEY_PACKAGE, packageName)
            putString(KEY_JSON, json.encodeToString(topics.toList()))
        })
    }

    fun hasPermission(packageName: String, permission: RuntimePermission): Boolean {
        val reply = transact(TRANSACTION_HAS_PERMISSION, Bundle().apply {
            putString(KEY_PACKAGE, packageName)
            putString(KEY_PERMISSION, permission.name)
        })
        return reply?.getBoolean(KEY_RESULT) ?: false
    }

    fun getPermissions(packageName: String): Set<RuntimePermission> {
        val reply = transact(TRANSACTION_GET_PERMISSIONS, Bundle().apply {
            putString(KEY_PACKAGE, packageName)
        }) ?: return emptySet()
        val jsonStr = reply.getString(KEY_JSON) ?: return emptySet()
        val names: List<String> = json.decodeFromString(jsonStr)
        return names.map { RuntimePermission.valueOf(it) }.toSet()
    }

    fun observeEvents(packageName: String): Flow<AppBoxEvent> = callbackFlow {
        val listener = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == TRANSACTION_EVENT_CALLBACK) {
                    val eventJson = data.readString() ?: return true
                    val event = json.decodeFromString<AppBoxEvent>(eventJson)
                    trySend(event)
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        }

        transact(TRANSACTION_EVENT_OBSERVE, Bundle().apply {
            putString(KEY_PACKAGE, packageName)
            putBinder(KEY_LISTENER, listener)
        })

        awaitClose {
            transact(TRANSACTION_EVENT_UNOBSERVE, Bundle().apply {
                putString(KEY_PACKAGE, packageName)
            })
        }
    }

    private fun transact(code: Int, data: Bundle): Bundle? {
        val parcel = Parcel.obtain()
        val replyParcel = Parcel.obtain()
        return try {
            data.writeToParcel(parcel, 0)
            remote.transact(code, parcel, replyParcel, 0)
            if (replyParcel.dataSize() > 0) {
                replyParcel.setDataPosition(0)
                Bundle.CREATOR.createFromParcel(replyParcel)
            } else {
                null
            }
        } finally {
            parcel.recycle()
            replyParcel.recycle()
        }
    }

    companion object {
        const val TRANSACTION_REGISTER = 1
        const val TRANSACTION_HEARTBEAT = 2
        const val TRANSACTION_AUTH = 3
        const val TRANSACTION_VALIDATE_SESSION = 4
        const val TRANSACTION_REVOKE_SESSION = 5
        const val TRANSACTION_STORAGE_PUT = 10
        const val TRANSACTION_STORAGE_GET = 11
        const val TRANSACTION_STORAGE_DELETE = 12
        const val TRANSACTION_STORAGE_LIST = 13
        const val TRANSACTION_NETWORK = 20
        const val TRANSACTION_NOTIFICATION = 30
        const val TRANSACTION_EVENT_PUBLISH = 40
        const val TRANSACTION_EVENT_SUBSCRIBE = 41
        const val TRANSACTION_EVENT_UNSUBSCRIBE = 42
        const val TRANSACTION_EVENT_OBSERVE = 43
        const val TRANSACTION_EVENT_UNOBSERVE = 44
        const val TRANSACTION_EVENT_CALLBACK = 45
        const val TRANSACTION_HAS_PERMISSION = 50
        const val TRANSACTION_GET_PERMISSIONS = 51

        const val KEY_PACKAGE = "package"
        const val KEY_JSON = "json"
        const val KEY_ERROR = "error"
        const val KEY_RESULT = "result"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_NAMESPACE = "namespace"
        const val KEY_KEY = "key"
        const val KEY_PERMISSION = "permission"
        const val KEY_LISTENER = "listener"
    }
}
