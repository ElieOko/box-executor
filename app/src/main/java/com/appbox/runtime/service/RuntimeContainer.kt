package com.appbox.runtime.service

import android.app.Application
import com.appbox.runtime.core.contract.AppRegistryContract
import com.appbox.runtime.core.contract.AuthServiceContract
import com.appbox.runtime.core.contract.EventBusContract
import com.appbox.runtime.core.contract.NetworkServiceContract
import com.appbox.runtime.core.contract.NotificationServiceContract
import com.appbox.runtime.core.contract.PermissionContract
import com.appbox.runtime.core.contract.RemoteMonitorContract
import com.appbox.runtime.core.contract.StorageServiceContract
import com.appbox.runtime.core.event.InProcessEventBus
import com.appbox.runtime.service.auth.AuthService
import com.appbox.runtime.service.manager.AppRegistry
import com.appbox.runtime.service.manager.InstalledAppScanner
import com.appbox.runtime.service.manager.LifecycleManager
import com.appbox.runtime.service.manager.PermissionManager
import com.appbox.runtime.service.network.NetworkService
import com.appbox.runtime.service.notification.NotificationService
import com.appbox.runtime.service.remote.RemoteMonitorStub
import com.appbox.runtime.service.storage.StorageService

class RuntimeContainer(application: Application) {
    val eventBus = InProcessEventBus()
    val remoteMonitor: RemoteMonitorContract = RemoteMonitorStub()

    val appRegistry: AppRegistryContract = AppRegistry(application, remoteMonitor)
    val installedAppScanner = InstalledAppScanner(application)
    val permissionManager: PermissionContract = PermissionManager(appRegistry, remoteMonitor)
    val authService: AuthServiceContract = AuthService(permissionManager, remoteMonitor)
    val storageService: StorageServiceContract = StorageService(application, permissionManager)
    val networkService: NetworkServiceContract = NetworkService(permissionManager, remoteMonitor)
    val notificationService: NotificationServiceContract =
        NotificationService(application, permissionManager)
    val lifecycleManager = LifecycleManager(appRegistry, remoteMonitor)
    val eventBusContract: EventBusContract = RuntimeEventBus(eventBus, permissionManager)
}
