package com.appbox.runtime.core.event

import com.appbox.runtime.core.model.AppBoxEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter

class InProcessEventBus {
    private val _events = MutableSharedFlow<AppBoxEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AppBoxEvent> = _events.asSharedFlow()

    private val subscriptions = mutableMapOf<String, MutableSet<String>>()

    suspend fun publish(event: AppBoxEvent): Result<Unit> = runCatching {
        _events.emit(event)
    }

    fun subscribe(packageName: String, topics: Set<String>) {
        val current = subscriptions.getOrPut(packageName) { mutableSetOf() }
        current.addAll(topics)
    }

    fun unsubscribe(packageName: String, topics: Set<String>) {
        subscriptions[packageName]?.removeAll(topics)
    }

    fun eventsFor(packageName: String) = events.filter { event ->
        val topics = subscriptions[packageName] ?: emptySet()
        event.targetPackage == null ||
            event.targetPackage == packageName ||
            event.targetPackage == "*" ||
            topics.contains(event.topic) ||
            topics.contains("*")
    }

    fun getSubscriptions(packageName: String): Set<String> =
        subscriptions[packageName]?.toSet() ?: emptySet()
}
