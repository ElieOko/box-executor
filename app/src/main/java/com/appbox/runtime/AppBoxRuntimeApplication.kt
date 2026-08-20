package com.appbox.runtime

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.appbox.runtime.service.RuntimeContainer

class AppBoxRuntimeApplication : Application() {

    lateinit var container: RuntimeContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = RuntimeContainer(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                RUNTIME_CHANNEL_ID,
                "AppBox Runtime Service",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val RUNTIME_CHANNEL_ID = "appbox_runtime_service"
        const val RUNTIME_NOTIFICATION_ID = 1001
    }
}
