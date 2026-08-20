package com.appbox.runtime.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import com.appbox.runtime.AppBoxRuntimeApplication
import com.appbox.runtime.core.model.AppBoxEvent
import com.appbox.runtime.core.model.AuthSession
import com.appbox.runtime.core.model.NetworkRequest
import com.appbox.runtime.core.model.NetworkResponse
import com.appbox.runtime.core.model.RuntimeNotification
import com.appbox.runtime.core.model.RuntimePermission
import com.appbox.runtime.core.model.StorageEntry
import com.appbox.runtime.core.security.RuntimeConstants
import com.appbox.runtime.sdk.internal.RuntimeBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RuntimeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private val eventListeners = mutableMapOf<String, IBinder>()

    private val container: RuntimeContainer
        get() = (application as AppBoxRuntimeApplication).container

  private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val bundle = Bundle()
            data.setDataPosition(0)
            bundle.readFromParcel(data)

            val result = handleTransaction(code, bundle)
            reply?.let {
                it.writeNoException()
                result.writeToParcel(it, 0)
            }
            return true
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        container.lifecycleManager.start()
    }

    private fun handleTransaction(code: Int, data: Bundle): Bundle {
        return try {
            when (code) {
                RuntimeBinder.TRANSACTION_REGISTER -> handleRegister(data)
                RuntimeBinder.TRANSACTION_HEARTBEAT -> handleHeartbeat(data)
                RuntimeBinder.TRANSACTION_AUTH -> handleAuth(data)
                RuntimeBinder.TRANSACTION_VALIDATE_SESSION -> handleValidateSession(data)
                RuntimeBinder.TRANSACTION_REVOKE_SESSION -> handleRevokeSession(data)
                RuntimeBinder.TRANSACTION_STORAGE_PUT -> handleStoragePut(data)
                RuntimeBinder.TRANSACTION_STORAGE_GET -> handleStorageGet(data)
                RuntimeBinder.TRANSACTION_STORAGE_DELETE -> handleStorageDelete(data)
                RuntimeBinder.TRANSACTION_STORAGE_LIST -> handleStorageList(data)
                RuntimeBinder.TRANSACTION_NETWORK -> handleNetwork(data)
                RuntimeBinder.TRANSACTION_NOTIFICATION -> handleNotification(data)
                RuntimeBinder.TRANSACTION_EVENT_PUBLISH -> handleEventPublish(data)
                RuntimeBinder.TRANSACTION_EVENT_SUBSCRIBE -> handleEventSubscribe(data)
                RuntimeBinder.TRANSACTION_EVENT_UNSUBSCRIBE -> handleEventUnsubscribe(data)
                RuntimeBinder.TRANSACTION_EVENT_OBSERVE -> handleEventObserve(data)
                RuntimeBinder.TRANSACTION_EVENT_UNOBSERVE -> handleEventUnobserve(data)
                RuntimeBinder.TRANSACTION_HAS_PERMISSION -> handleHasPermission(data)
                RuntimeBinder.TRANSACTION_GET_PERMISSIONS -> handleGetPermissions(data)
                else -> errorBundle("Unknown transaction: $code")
            }
        } catch (e: Exception) {
            errorBundle(e.message ?: "Unknown error")
        }
    }

    private fun handleRegister(data: Bundle): Bundle {
        val packageName = data.getString(RuntimeBinder.KEY_PACKAGE) ?: return errorBundle("Missing package")
        scope.launch {
            container.lifecycleManager.onClientConnected(packageName)
        }
        return Bundle()
    }

    private fun handleHeartbeat(data: Bundle): Bundle {
        val packageName = data.getString(RuntimeBinder.KEY_PACKAGE) ?: return errorBundle("Missing package")
        container.lifecycleManager.recordHeartbeat(packageName)
        return Bundle().apply { putBoolean(RuntimeBinder.KEY_RESULT, true) }
    }

    private fun handleAuth(data: Bundle): Bundle {
        val packageName = data.getString(RuntimeBinder.KEY_PACKAGE) ?: return errorBundle("Missing package")
        val credentialsJson = data.getString(RuntimeBinder.KEY_JSON) ?: return errorBundle("Missing credentials")
        val credentials: Map<String, String> = json.decodeFromString(credentialsJson)

        val result = kotlinx.coroutines.runBlocking {
            container.authService.authenticate(packageName, credentials)
        }
        return result.fold(
            onSuccess = { session ->
                Bundle().apply { putString(RuntimeBinder.KEY_JSON, json.encodeToString(session)) }
            },
            onFailure = { errorBundle(it.message ?: "Auth failed") },
        )
    }

    private fun handleValidateSession(data: Bundle): Bundle {
        val sessionId = data.getString(RuntimeBinder.KEY_SESSION_ID) ?: return errorBundle("Missing session")
        val session = kotlinx.coroutines.runBlocking {
            container.authService.validateSession(sessionId)
        }
        return if (session != null) {
            Bundle().apply { putString(RuntimeBinder.KEY_JSON, json.encodeToString(session)) }
        } else {
            Bundle()
        }
    }

    private fun handleRevokeSession(data: Bundle): Bundle {
        val sessionId = data.getString(RuntimeBinder.KEY_SESSION_ID) ?: return errorBundle("Missing session")
        kotlinx.coroutines.runBlocking {
            container.authService.revokeSession(sessionId)
        }
        return Bundle()
    }

    private fun handleStoragePut(data: Bundle): Bundle {
        val entryJson = data.getString(RuntimeBinder.KEY_JSON) ?: return errorBundle("Missing entry")
        val entry: StorageEntry = json.decodeFromString(entryJson)
        val result = kotlinx.coroutines.runBlocking { container.storageService.put(entry) }
        return result.fold(
            onSuccess = { Bundle() },
            onFailure = { errorBundle(it.message ?: "Storage put failed") },
        )
    }

    private fun handleStorageGet(data: Bundle): Bundle {
        val namespace = data.getString(RuntimeBinder.KEY_NAMESPACE) ?: return errorBundle("Missing namespace")
        val key = data.getString(RuntimeBinder.KEY_KEY) ?: return errorBundle("Missing key")
        val requester = data.getString(RuntimeBinder.KEY_PACKAGE) ?: return errorBundle("Missing package")
        val entry = kotlinx.coroutines.runBlocking {
            container.storageService.get(namespace, key, requester)
        }
        return if (entry != null) {
            Bundle().apply { putString(RuntimeBinder.KEY_JSON, json.encodeToString(entry)) }
        } else {
            Bundle()
        }
    }

    private fun handleStorageDelete(data: Bundle): Bundle {
        val namespace = data.getString(RuntimeBinder.KEY_NAMESPACE) ?: return errorBundle("Missing namespace")
        val key = data.getString(RuntimeBinder.KEY_KEY) ?: return errorBundle("Missing key")
        val requester = data.getString(RuntimeBinder.KEY_PACKAGE) ?: return errorBundle("Missing package")
        val result = kotlinx.coroutines.runBlocking {
            container.storageService.delete(namespace, key, requester)
        }
        return result.fold(
            onSuccess = { Bundle() },
            onFailure = { errorBundle(it.message ?: "Storage delete failed") },
        )
    }

    private fun handleStorageList(data: Bundle): Bundle {
        val namespace = data.getString(RuntimeBinder.KEY_NAMESPACE) ?: return errorBundle("Missing namespace")
        val requester = data.getString(RuntimeBinder.KEY_PACKAGE) ?: return errorBundle("Missing package")
        val entries = kotlinx.coroutines.runBlocking {
            container.storageService.list(namespace, requester)
        }
        return Bundle().apply {
            putString(RuntimeBinder.KEY_JSON, json.encodeToString(entries))
        }
    }

    private fun handleNetwork(data: Bundle): Bundle {
        val requestJson = data.getString(RuntimeBinder.KEY_JSON) ?: return errorBundle("Missing request")
        val request: NetworkRequest = json.decodeFromString(requestJson)
        val result = kotlinx.coroutines.runBlocking { container.networkService.execute(request) }
        return result.fold(
            onSuccess = { response ->
                Bundle().apply { putString(RuntimeBinder.KEY_JSON, json.encodeToString(response)) }
            },
            onFailure = { errorBundle(it.message ?: "Network failed") },
        )
    }

    private fun handleNotification(data: Bundle): Bundle {
        val notificationJson = data.getString(RuntimeBinder.KEY_JSON) ?: return errorBundle("Missing notification")
        val notification: RuntimeNotification = json.decodeFromString(notificationJson)
        val result = kotlinx.coroutines.runBlocking { container.notificationService.post(notification) }
        return result.fold(
            onSuccess = { Bundle() },
            onFailure = { errorBundle(it.message ?: "Notification failed") },
        )
    }

    private fun handleEventPublish(data: Bundle): Bundle {
        val eventJson = data.getString(RuntimeBinder.KEY_JSON) ?: return errorBundle("Missing event")
        val event: AppBoxEvent = json.decodeFromString(eventJson)
        val result = kotlinx.coroutines.runBlocking { container.eventBusContract.publish(event) }
        result.onSuccess {
            scope.launch {
                notifyEventListeners(event)
            }
        }
        return result.fold(
            onSuccess = { Bundle() },
            onFailure = { errorBundle(it.message ?: "Event publish failed") },
        )
    }

    private fun handleEventSubscribe(data: Bundle): Bundle {
        val packageName = data.getString(RuntimeBinder.KEY_PACKAGE) ?: return errorBundle("Missing package")
        val topicsJson = data.getString(RuntimeBinder.KEY_JSON) ?: return errorBundle("Missing topics")
        val topics: List<String> = json.decodeFromString(topicsJson)
        val result = kotlinx.coroutines.runBlocking {
            container.eventBusContract.subscribe(packageName, topics.toSet())
        }
        return result.fold(
            onSuccess = { Bundle() },
            onFailure = { errorBundle(it.message ?: "Subscribe failed") },
        )
    }

    private fun handleEventUnsubscribe(data: Bundle): Bundle {
        val packageName = data.getString(RuntimeBinder.KEY_PACKAGE) ?: return errorBundle("Missing package")
        val topicsJson = data.getString(RuntimeBinder.KEY_JSON) ?: return errorBundle("Missing topics")
        val topics: List<String> = json.decodeFromString(topicsJson)
        val result = kotlinx.coroutines.runBlocking {
            container.eventBusContract.unsubscribe(packageName, topics.toSet())
        }
        return result.fold(
            onSuccess = { Bundle() },
            onFailure = { errorBundle(it.message ?: "Unsubscribe failed") },
        )
    }

    private fun handleEventObserve(data: Bundle): Bundle {
        val packageName = data.getString(RuntimeBinder.KEY_PACKAGE) ?: return errorBundle("Missing package")
        val listener = data.getBinder(RuntimeBinder.KEY_LISTENER)
        if (listener != null) {
            eventListeners[packageName] = listener
        }
        return Bundle()
    }

    private fun handleEventUnobserve(data: Bundle): Bundle {
        val packageName = data.getString(RuntimeBinder.KEY_PACKAGE) ?: return errorBundle("Missing package")
        eventListeners.remove(packageName)
        return Bundle()
    }

    private fun handleHasPermission(data: Bundle): Bundle {
        val packageName = data.getString(RuntimeBinder.KEY_PACKAGE) ?: return errorBundle("Missing package")
        val permissionName = data.getString(RuntimeBinder.KEY_PERMISSION) ?: return errorBundle("Missing permission")
        val permission = RuntimePermission.valueOf(permissionName)
        val has = kotlinx.coroutines.runBlocking {
            container.permissionManager.hasPermission(packageName, permission)
        }
        return Bundle().apply { putBoolean(RuntimeBinder.KEY_RESULT, has) }
    }

    private fun handleGetPermissions(data: Bundle): Bundle {
        val packageName = data.getString(RuntimeBinder.KEY_PACKAGE) ?: return errorBundle("Missing package")
        val permissions = kotlinx.coroutines.runBlocking {
            container.permissionManager.getPermissions(packageName)
        }
        return Bundle().apply {
            putString(RuntimeBinder.KEY_JSON, json.encodeToString(permissions.map { it.name }))
        }
    }

    private fun notifyEventListeners(event: AppBoxEvent) {
        val eventJson = json.encodeToString(event)
        val parcel = Parcel.obtain()
        try {
            parcel.writeString(eventJson)
            eventListeners.forEach { (packageName, listener) ->
                if (event.targetPackage == null ||
                    event.targetPackage == packageName ||
                    event.targetPackage == "*"
                ) {
                    try {
                        listener.transact(RuntimeBinder.TRANSACTION_EVENT_CALLBACK, parcel, null, IBinder.FLAG_ONEWAY)
                    } catch (_: Exception) {
                    }
                }
            }
        } finally {
            parcel.recycle()
        }
    }

    private fun errorBundle(message: String) = Bundle().apply {
        putString(RuntimeBinder.KEY_ERROR, message)
    }
}
