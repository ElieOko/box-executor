package com.appbox.runtime.service.remote

import android.util.Log
import com.appbox.runtime.core.contract.RemoteMonitorContract
import com.appbox.runtime.core.model.RemoteMonitorEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Stub for future central server integration.
 * Events are buffered locally and can be flushed when a remote endpoint is configured.
 */
class RemoteMonitorStub : RemoteMonitorContract {

    private val _events = MutableSharedFlow<RemoteMonitorEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<RemoteMonitorEvent> = _events.asSharedFlow()

    private val buffer = mutableListOf<RemoteMonitorEvent>()
    private var connected = false

    override suspend fun report(event: RemoteMonitorEvent) {
        buffer.add(event)
        _events.emit(event)
        Log.d(TAG, "[${event.type}] ${event.packageName ?: "system"}: ${event.message}")
    }

    override fun isConnected(): Boolean = connected

    fun connect(endpoint: String) {
        connected = true
        Log.i(TAG, "Remote monitor connected to $endpoint (stub)")
    }

    fun disconnect() {
        connected = false
    }

    fun getBufferedEvents(): List<RemoteMonitorEvent> = buffer.toList()

    fun clearBuffer() {
        buffer.clear()
    }

    companion object {
        private const val TAG = "AppBoxRemoteMonitor"
    }
}
