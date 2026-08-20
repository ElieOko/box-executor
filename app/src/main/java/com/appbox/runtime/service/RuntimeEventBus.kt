package com.appbox.runtime.service

import com.appbox.runtime.core.contract.EventBusContract
import com.appbox.runtime.core.contract.PermissionContract
import com.appbox.runtime.core.event.InProcessEventBus
import com.appbox.runtime.core.model.AppBoxEvent
import com.appbox.runtime.core.model.RuntimePermission

class RuntimeEventBus(
    private val bus: InProcessEventBus,
    private val permissions: PermissionContract,
) : EventBusContract {

    override suspend fun publish(event: AppBoxEvent): Result<Unit> {
        if (!permissions.hasPermission(event.sourcePackage, RuntimePermission.EVENTS_PUBLISH)) {
            return Result.failure(SecurityException("EVENTS_PUBLISH not granted for ${event.sourcePackage}"))
        }
        return bus.publish(event)
    }

    override suspend fun subscribe(packageName: String, topics: Set<String>): Result<Unit> = runCatching {
        if (!permissions.hasPermission(packageName, RuntimePermission.EVENTS_SUBSCRIBE)) {
            throw SecurityException("EVENTS_SUBSCRIBE not granted for $packageName")
        }
        bus.subscribe(packageName, topics)
    }

    override suspend fun unsubscribe(packageName: String, topics: Set<String>): Result<Unit> = runCatching {
        bus.unsubscribe(packageName, topics)
    }
}
