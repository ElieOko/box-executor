package com.appbox.runtime.core.security

object RuntimeConstants {
    const val RUNTIME_PACKAGE = "com.appbox.runtime"
    const val RUNTIME_SERVICE_ACTION = "com.appbox.runtime.BIND_RUNTIME"
    const val AUTHORITY_STORAGE = "com.appbox.runtime.storage"
    const val AUTHORITY_EVENTS = "com.appbox.runtime.events"

    const val EXTRA_PACKAGE_NAME = "extra_package_name"
    const val EXTRA_SESSION_ID = "extra_session_id"
    const val EXTRA_EVENT_TOPIC = "extra_event_topic"

    const val DEFAULT_STORAGE_NAMESPACE = "default"
    const val SYSTEM_NAMESPACE = "system"
}

data class TrustedApp(
    val packageName: String,
    val signatureHash: String,
    val displayName: String,
    val defaultPermissions: Set<com.appbox.runtime.core.model.RuntimePermission> = emptySet(),
)

object SignatureVerifier {
    fun isTrusted(
        packageName: String,
        signatureHash: String,
        trustedApps: List<TrustedApp>,
    ): Boolean {
        return trustedApps.any {
            it.packageName == packageName && it.signatureHash == signatureHash
        }
    }
}
