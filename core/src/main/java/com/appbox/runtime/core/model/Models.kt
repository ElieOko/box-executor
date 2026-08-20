package com.appbox.runtime.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AppBoxApp(
    val packageName: String,
    val displayName: String,
    val versionName: String,
    val versionCode: Long,
    val signatureHash: String,
    val state: AppLifecycleState = AppLifecycleState.REGISTERED,
    val permissions: Set<RuntimePermission> = emptySet(),
    val installedAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long? = null,
)

@Serializable
enum class AppLifecycleState {
    REGISTERED,
    ACTIVE,
    SUSPENDED,
    STOPPED,
    UNINSTALLED,
}

@Serializable
enum class RuntimePermission {
    AUTH_READ,
    AUTH_WRITE,
    STORAGE_READ,
    STORAGE_WRITE,
    NETWORK_ACCESS,
    NETWORK_PROXY,
    NOTIFICATIONS_POST,
    NOTIFICATIONS_READ,
    EVENTS_PUBLISH,
    EVENTS_SUBSCRIBE,
    INTER_APP_CALL,
}

@Serializable
data class PermissionPolicy(
    val packageName: String,
    val granted: Set<RuntimePermission>,
    val denied: Set<RuntimePermission> = emptySet(),
)

@Serializable
data class AppBoxEvent(
    val id: String,
    val topic: String,
    val sourcePackage: String,
    val targetPackage: String? = null,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis(),
    val priority: EventPriority = EventPriority.NORMAL,
)

@Serializable
enum class EventPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL,
}

@Serializable
data class AuthSession(
    val sessionId: String,
    val userId: String,
    val packageName: String,
    val accessToken: String,
    val expiresAt: Long,
    val scopes: Set<String> = emptySet(),
)

@Serializable
data class StorageEntry(
    val key: String,
    val namespace: String,
    val ownerPackage: String,
    val value: String,
    val encrypted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class NetworkRequest(
    val id: String,
    val packageName: String,
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

@Serializable
data class NetworkResponse(
    val id: String,
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val error: String? = null,
)

@Serializable
data class RuntimeNotification(
    val id: String,
    val sourcePackage: String,
    val title: String,
    val body: String,
    val channelId: String = "appbox_default",
    val data: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class RemoteMonitorEvent(
    val type: MonitorEventType,
    val packageName: String? = null,
    val message: String,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
enum class MonitorEventType {
    APP_REGISTERED,
    APP_UNREGISTERED,
    APP_STATE_CHANGED,
    PERMISSION_CHANGED,
    AUTH_EVENT,
    NETWORK_REQUEST,
    ERROR,
    HEARTBEAT,
}
