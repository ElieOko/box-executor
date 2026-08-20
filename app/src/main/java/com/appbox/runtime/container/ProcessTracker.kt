package com.appbox.runtime.container

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.provider.Settings
import com.appbox.runtime.core.model.TrackedProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProcessTracker(
    private val context: Context,
) {
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    suspend fun scanRegisteredProcesses(
        registeredApps: Map<String, String>,
    ): List<TrackedProcess> = withContext(Dispatchers.Default) {
        val foregroundPackage = getForegroundPackage()
        val running = activityManager.runningAppProcesses.orEmpty()
        val memoryByPid = loadMemoryMap(running.map { it.pid }.toIntArray())

        registeredApps.flatMap { (packageName, displayName) ->
            running
                .filter { proc ->
                    proc.processName == packageName ||
                        proc.pkgList?.contains(packageName) == true
                }
                .map { proc ->
                    TrackedProcess(
                        packageName = packageName,
                        displayName = displayName,
                        pid = proc.pid,
                        uid = proc.uid,
                        importance = proc.importance,
                        importanceLabel = labelForImportance(proc.importance),
                        memoryPssKb = memoryByPid[proc.pid] ?: 0L,
                        isForeground = foregroundPackage == packageName ||
                            proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE,
                    )
                }
        }.distinctBy { "${it.packageName}_${it.pid}" }
            .sortedByDescending { it.isForeground }
    }

    fun getProcessForPackage(packageName: String): TrackedProcess? {
        val proc = activityManager.runningAppProcesses
            ?.firstOrNull {
                it.processName == packageName || it.pkgList?.contains(packageName) == true
            }
            ?: return null

        val memory = loadMemoryMap(intArrayOf(proc.pid))[proc.pid] ?: 0L
        return TrackedProcess(
            packageName = packageName,
            displayName = packageName,
            pid = proc.pid,
            uid = proc.uid,
            importance = proc.importance,
            importanceLabel = labelForImportance(proc.importance),
            memoryPssKb = memory,
            isForeground = proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
        )
    }

    fun isRunning(packageName: String): Boolean {
        return activityManager.runningAppProcesses?.any {
            it.processName == packageName || it.pkgList?.contains(packageName) == true
        } == true
    }

    fun stopProcess(packageName: String) {
        activityManager.killBackgroundProcesses(packageName)
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessIntent() = Settings.ACTION_USAGE_ACCESS_SETTINGS

    fun getForegroundPackage(): String? = getForegroundPackageInternal()

    private fun getForegroundPackageInternal(): String? {
        if (!hasUsageAccess()) return null
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val begin = end - 60_000L
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end)
        return stats
            ?.filter { it.lastTimeUsed > 0 }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    private fun loadMemoryMap(pids: IntArray): Map<Int, Long> {
        if (pids.isEmpty()) return emptyMap()
        return runCatching {
            val infos = activityManager.getProcessMemoryInfo(pids)
            pids.indices.associate { index ->
                pids[index] to infos[index].totalPss.toLong()
            }
        }.getOrDefault(emptyMap())
    }

    private fun labelForImportance(importance: Int): String = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
        -> "Premier plan"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE,
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE,
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE_PRE_26,
        -> "Visible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "Service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "En cache"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "Arrêté"
        else -> "Inconnu"
    }
}
