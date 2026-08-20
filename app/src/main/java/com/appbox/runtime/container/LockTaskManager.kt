package com.appbox.runtime.container

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.appbox.runtime.admin.AppBoxDeviceAdminReceiver

object LockTaskManager {

    /** Épinglage écran — désactivé temporairement par défaut */
    @Volatile
    var enabled: Boolean = false

    fun enterLockTask(activity: Activity) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        runCatching { activity.startLockTask() }
    }

    fun exitLockTask(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        runCatching { activity.stopLockTask() }
    }

    fun isInLockTask(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val am = context.getSystemService(ActivityManager::class.java)
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, AppBoxDeviceAdminReceiver::class.java)
        return dpm.isDeviceOwnerApp(context.packageName) ||
            dpm.isProfileOwnerApp(context.packageName)
    }

    fun canUseFullLockTask(context: Context): Boolean = isDeviceOwner(context)

    fun whitelistPackages(context: Context, packages: List<String>) {
        if (!isDeviceOwner(context)) return
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, AppBoxDeviceAdminReceiver::class.java)
        dpm.setLockTaskPackages(admin, packages.toTypedArray())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.setLockTaskFeatures(
                admin,
                DevicePolicyManager.LOCK_TASK_FEATURE_NONE,
            )
        }
    }

    fun syncWhitelist(context: Context, registeredPackages: List<String>) {
        val packages = (listOf(context.packageName) + registeredPackages).distinct()
        whitelistPackages(context, packages)
    }
}
