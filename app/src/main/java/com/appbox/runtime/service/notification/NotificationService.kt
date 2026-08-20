package com.appbox.runtime.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.appbox.runtime.core.contract.NotificationServiceContract
import com.appbox.runtime.core.contract.PermissionContract
import com.appbox.runtime.core.model.RuntimeNotification
import com.appbox.runtime.core.model.RuntimePermission
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NotificationService(
    private val context: Context,
    private val permissions: PermissionContract,
) : NotificationServiceContract {

    private val mutex = Mutex()
    private val history = mutableListOf<RuntimeNotification>()
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createDefaultChannel()
    }

    override suspend fun post(notification: RuntimeNotification): Result<Unit> = mutex.withLock {
        runCatching {
            if (!permissions.hasPermission(notification.sourcePackage, RuntimePermission.NOTIFICATIONS_POST)) {
                throw SecurityException("NOTIFICATIONS_POST not granted for ${notification.sourcePackage}")
            }

            val androidNotification = NotificationCompat.Builder(context, notification.channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(notification.title)
                .setContentText(notification.body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            notificationManager.notify(notification.id.hashCode(), androidNotification)
            history.add(notification)
            Unit
        }
    }

    override suspend fun getHistory(packageName: String, limit: Int): List<RuntimeNotification> =
        mutex.withLock {
            if (!permissions.hasPermission(packageName, RuntimePermission.NOTIFICATIONS_READ)) {
                return emptyList()
            }
            history.filter { it.sourcePackage == packageName }.takeLast(limit)
        }

    private fun createDefaultChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "appbox_default",
                "AppBox Notifications",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
