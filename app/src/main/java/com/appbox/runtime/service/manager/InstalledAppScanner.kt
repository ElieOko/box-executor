package com.appbox.runtime.service.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.appbox.runtime.core.model.AppBoxApp

data class InstalledAppCandidate(
    val packageName: String,
    val displayName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
)

class InstalledAppScanner(
    private val context: Context,
) {
    private val runtimePackage = context.packageName

    fun scanLaunchableApps(): List<InstalledAppCandidate> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

        return activities
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.applicationInfo.packageName
                if (packageName == runtimePackage) return@mapNotNull null

                runCatching {
                    val appInfo = resolveInfo.activityInfo.applicationInfo
                    val packageInfo = pm.getPackageInfo(packageName, 0)
                    InstalledAppCandidate(
                        packageName = packageName,
                        displayName = pm.getApplicationLabel(appInfo).toString(),
                        versionName = packageInfo.versionName ?: "?",
                        versionCode = packageInfo.longVersionCode,
                        isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                }.getOrNull()
            }
            .distinctBy { it.packageName }
            .sortedBy { it.displayName.lowercase() }
    }

    fun loadIcon(packageName: String): Drawable? = runCatching {
        context.packageManager.getApplicationIcon(packageName)
    }.getOrNull()

    fun createLaunchIntent(packageName: String): Intent? {
        val pm = context.packageManager
        return pm.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
