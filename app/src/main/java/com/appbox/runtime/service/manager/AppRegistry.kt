package com.appbox.runtime.service.manager

import android.content.Context
import android.content.pm.PackageManager
import com.appbox.runtime.core.config.HostAppCatalog
import com.appbox.runtime.core.config.HostAppDefinition
import com.appbox.runtime.core.contract.AppRegistryContract
import com.appbox.runtime.core.contract.RemoteMonitorContract
import com.appbox.runtime.core.model.AppBoxApp
import com.appbox.runtime.core.model.AppLifecycleState
import com.appbox.runtime.core.model.MonitorEventType
import com.appbox.runtime.core.model.RemoteMonitorEvent
import com.appbox.runtime.core.model.RuntimePermission
import com.appbox.runtime.core.security.TrustedApp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

class AppRegistry(
    private val context: Context,
    private val remoteMonitor: RemoteMonitorContract,
) : AppRegistryContract {

    private val mutex = Mutex()
    private val apps = mutableMapOf<String, AppBoxApp>()
    private val store = AppRegistryStore(context)

    private val trustedApps = listOf(
        TrustedApp(
            packageName = "com.appbox.runtime",
            signatureHash = "runtime_self",
            displayName = "AppBox Runtime",
            defaultPermissions = RuntimePermission.entries.toSet(),
        ),
    )

    suspend fun initialize() {
        mutex.withLock {
            apps.clear()
            store.loadApps().forEach { app ->
                apps[app.packageName] = app
            }
        }
        ensureBundledHostApps()
    }

    /** Enregistre automatiquement les apps du catalogue si elles sont installées. */
    suspend fun ensureBundledHostApps(): List<AppBoxApp> {
        val registered = mutableListOf<AppBoxApp>()
        HostAppCatalog.apps.forEach { definition ->
            if (!definition.autoRegisterOnStartup) return@forEach
            if (getApp(definition.packageName) != null) return@forEach
            if (!isPackageInstalled(definition.packageName)) return@forEach

            registerFromPackage(definition.packageName)
                .onSuccess { registered.add(it) }
        }
        return registered
    }

    override suspend fun registerApp(app: AppBoxApp): Result<AppBoxApp> = mutex.withLock {
        runCatching {
            val signatureHash = resolveSignatureHash(app.packageName)
            val hostDef = HostAppCatalog.find(app.packageName)
            val trusted = trustedApps.find { it.packageName == app.packageName }
            val registered = app.copy(
                signatureHash = signatureHash,
                permissions = when {
                    trusted != null -> trusted.defaultPermissions
                    hostDef != null -> hostDef.permissions
                    app.permissions.isNotEmpty() -> app.permissions
                    else -> defaultUserPermissions
                },
                state = AppLifecycleState.ACTIVE,
                lastActiveAt = System.currentTimeMillis(),
            )
            apps[app.packageName] = registered
            persistLocked()
            remoteMonitor.report(
                RemoteMonitorEvent(
                    type = MonitorEventType.APP_REGISTERED,
                    packageName = app.packageName,
                    message = "App registered: ${app.displayName}",
                ),
            )
            registered
        }
    }

    override suspend fun unregisterApp(packageName: String): Result<Unit> = mutex.withLock {
        runCatching {
            apps.remove(packageName)
            persistLocked()
            remoteMonitor.report(
                RemoteMonitorEvent(
                    type = MonitorEventType.APP_UNREGISTERED,
                    packageName = packageName,
                    message = "App removed from AppBox",
                ),
            )
            Unit
        }
    }

    override suspend fun getApp(packageName: String): AppBoxApp? = mutex.withLock {
        apps[packageName]
    }

    override suspend fun getAllApps(): List<AppBoxApp> = mutex.withLock {
        apps.values.sortedBy { it.displayName.lowercase() }
    }

    override suspend fun updateAppState(
        packageName: String,
        state: AppLifecycleState,
    ): Result<Unit> = mutex.withLock {
        runCatching {
            val app = apps[packageName] ?: throw IllegalArgumentException("App not found: $packageName")
            apps[packageName] = app.copy(
                state = state,
                lastActiveAt = if (state == AppLifecycleState.ACTIVE) {
                    System.currentTimeMillis()
                } else {
                    app.lastActiveAt
                },
            )
            persistLocked()
            remoteMonitor.report(
                RemoteMonitorEvent(
                    type = MonitorEventType.APP_STATE_CHANGED,
                    packageName = packageName,
                    message = "State changed to $state",
                ),
            )
            Unit
        }
    }

    suspend fun updateAppPermissions(packageName: String, permissions: Set<RuntimePermission>): Result<Unit> =
        mutex.withLock {
            runCatching {
                val app = apps[packageName] ?: throw IllegalArgumentException("App not found: $packageName")
                apps[packageName] = app.copy(permissions = permissions)
                persistLocked()
                Unit
            }
        }

    suspend fun registerFromPackage(packageName: String): Result<AppBoxApp> {
        if (!isPackageInstalled(packageName)) {
            return Result.failure(
                PackageManager.NameNotFoundException(
                    "Application non installée: $packageName",
                ),
            )
        }

        val pm = context.packageManager
        return runCatching {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val catalogName = HostAppCatalog.find(packageName)?.displayName
            val displayName = catalogName ?: pm.getApplicationLabel(appInfo).toString()
            registerApp(
                AppBoxApp(
                    packageName = packageName,
                    displayName = displayName,
                    versionName = info.versionName ?: "unknown",
                    versionCode = info.longVersionCode,
                    signatureHash = resolveSignatureHash(packageName),
                    permissions = HostAppCatalog.find(packageName)?.permissions ?: defaultUserPermissions,
                ),
            ).getOrThrow()
        }
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun resolveSignatureHash(packageName: String): String {
        return try {
            val signatures = context.packageManager
                .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                ?.apkContentsSigners
                ?: return "unknown"

            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(signatures.first().toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "unknown"
        }
    }

    fun isTrusted(packageName: String): Boolean {
        if (HostAppCatalog.isCatalogApp(packageName) && apps.containsKey(packageName)) {
            val def = HostAppCatalog.find(packageName) ?: return false
            if (def.signatureHash == null) return true
            return def.signatureHash == resolveSignatureHash(packageName)
        }
        val hash = resolveSignatureHash(packageName)
        return trustedApps.any { it.packageName == packageName && it.signatureHash == hash } ||
            trustedApps.any { it.packageName == packageName && it.signatureHash != "runtime_self" }
    }

    fun getPendingCatalogApps(): List<HostAppDefinition> {
        return HostAppCatalog.apps.filter { def ->
            def.autoRegisterOnStartup &&
                !apps.containsKey(def.packageName) &&
                isPackageInstalled(def.packageName)
        }
    }

    private suspend fun persistLocked() {
        store.saveApps(apps.values.toList())
    }

    companion object {
        val defaultUserPermissions = setOf(
            RuntimePermission.STORAGE_READ,
            RuntimePermission.STORAGE_WRITE,
            RuntimePermission.EVENTS_PUBLISH,
            RuntimePermission.EVENTS_SUBSCRIBE,
            RuntimePermission.NOTIFICATIONS_POST,
            RuntimePermission.AUTH_READ,
            RuntimePermission.INTER_APP_CALL,
        )
    }
}
