package com.appbox.runtime.service.manager

import android.content.Context
import android.content.pm.PackageManager
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

    private val trustedApps = listOf(
        TrustedApp(
            packageName = "com.appbox.runtime",
            signatureHash = "runtime_self",
            displayName = "AppBox Runtime",
            defaultPermissions = RuntimePermission.entries.toSet(),
        ),
    )

    override suspend fun registerApp(app: AppBoxApp): Result<AppBoxApp> = mutex.withLock {
        runCatching {
            val signatureHash = resolveSignatureHash(app.packageName)
            val trusted = trustedApps.find { it.packageName == app.packageName }
            val registered = app.copy(
                signatureHash = signatureHash,
                permissions = trusted?.defaultPermissions ?: app.permissions,
                state = AppLifecycleState.ACTIVE,
                lastActiveAt = System.currentTimeMillis(),
            )
            apps[app.packageName] = registered
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
            remoteMonitor.report(
                RemoteMonitorEvent(
                    type = MonitorEventType.APP_UNREGISTERED,
                    packageName = packageName,
                    message = "App unregistered",
                ),
            )
        }
    }

    override suspend fun getApp(packageName: String): AppBoxApp? = mutex.withLock {
        apps[packageName]
    }

    override suspend fun getAllApps(): List<AppBoxApp> = mutex.withLock {
        apps.values.toList()
    }

    override suspend fun updateAppState(
        packageName: String,
        state: AppLifecycleState,
    ): Result<Unit> = mutex.withLock {
        runCatching {
            val app = apps[packageName] ?: throw IllegalArgumentException("App not found: $packageName")
            apps[packageName] = app.copy(
                state = state,
                lastActiveAt = if (state == AppLifecycleState.ACTIVE) System.currentTimeMillis() else app.lastActiveAt,
            )
            remoteMonitor.report(
                RemoteMonitorEvent(
                    type = MonitorEventType.APP_STATE_CHANGED,
                    packageName = packageName,
                    message = "State changed to $state",
                ),
            )
        }
    }

    suspend fun registerFromPackage(packageName: String): Result<AppBoxApp> {
        val pm = context.packageManager
        return runCatching {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val displayName = pm.getApplicationLabel(appInfo).toString()
            registerApp(
                AppBoxApp(
                    packageName = packageName,
                    displayName = displayName,
                    versionName = info.versionName ?: "unknown",
                    versionCode = info.longVersionCode,
                    signatureHash = resolveSignatureHash(packageName),
                ),
            ).getOrThrow()
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
        val hash = resolveSignatureHash(packageName)
        return trustedApps.any { it.packageName == packageName && it.signatureHash == hash } ||
            trustedApps.any { it.packageName == packageName && it.signatureHash != "runtime_self" }
    }
}
