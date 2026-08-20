package com.appbox.runtime.core.config

import com.appbox.runtime.core.model.RuntimePermission

/**
 * Applications métier autorisées à tourner dans l'environnement AppBox.
 * Ajoutez vos packages ici — Device Owner n'est pas requis pour les exécuter
 * dans la box (Lock Task basique + VirtualDisplay).
 */
data class HostAppDefinition(
    val packageName: String,
    val displayName: String,
    val autoRegisterOnStartup: Boolean = true,
    /** null = toute signature installée sur l'appareil est acceptée */
    val signatureHash: String? = null,
    val permissions: Set<RuntimePermission> = HostAppCatalog.defaultHostPermissions,
)

object HostAppCatalog {

    val defaultHostPermissions = setOf(
        RuntimePermission.STORAGE_READ,
        RuntimePermission.STORAGE_WRITE,
        RuntimePermission.EVENTS_PUBLISH,
        RuntimePermission.EVENTS_SUBSCRIBE,
        RuntimePermission.NOTIFICATIONS_POST,
        RuntimePermission.NOTIFICATIONS_READ,
        RuntimePermission.AUTH_READ,
        RuntimePermission.AUTH_WRITE,
        RuntimePermission.NETWORK_ACCESS,
        RuntimePermission.INTER_APP_CALL,
    )

    /** Apps hôtes configurées pour AppBox Runtime */
    val apps: List<HostAppDefinition> = listOf(
        HostAppDefinition(
            packageName = "com.yvent.app",
            displayName = "Yvent",
            autoRegisterOnStartup = true,
        ),
    )

    fun find(packageName: String): HostAppDefinition? =
        apps.find { it.packageName == packageName }

    fun isCatalogApp(packageName: String): Boolean =
        apps.any { it.packageName == packageName }

    fun packageNames(): List<String> = apps.map { it.packageName }
}
