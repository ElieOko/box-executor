package com.appbox.runtime.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appbox.runtime.core.model.AppBoxApp
import com.appbox.runtime.core.model.AppLifecycleState
import com.appbox.runtime.core.model.RemoteMonitorEvent
import com.appbox.runtime.core.model.RuntimePermission
import com.appbox.runtime.service.RuntimeContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RuntimeUiState(
    val apps: List<AppBoxApp> = emptyList(),
    val selectedApp: AppBoxApp? = null,
    val monitorEvents: List<RemoteMonitorEvent> = emptyList(),
    val isRuntimeActive: Boolean = true,
    val error: String? = null,
)

class RuntimeViewModel(
    private val container: RuntimeContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RuntimeUiState())
    val uiState: StateFlow<RuntimeUiState> = _uiState.asStateFlow()

    init {
        refreshApps()
        observeMonitorEvents()
    }

    fun refreshApps() {
        viewModelScope.launch {
            val apps = container.appRegistry.getAllApps()
            _uiState.update { it.copy(apps = apps, error = null) }
        }
    }

    fun selectApp(app: AppBoxApp?) {
        _uiState.update { it.copy(selectedApp = app) }
    }

    fun grantPermission(packageName: String, permission: RuntimePermission) {
        viewModelScope.launch {
            container.permissionManager.grant(packageName, permission)
                .onSuccess { refreshApps() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun revokePermission(packageName: String, permission: RuntimePermission) {
        viewModelScope.launch {
            container.permissionManager.revoke(packageName, permission)
                .onSuccess { refreshApps() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun suspendApp(packageName: String) {
        viewModelScope.launch {
            container.lifecycleManager.suspendApp(packageName)
            refreshApps()
        }
    }

    fun stopApp(packageName: String) {
        viewModelScope.launch {
            container.lifecycleManager.stopApp(packageName)
            refreshApps()
        }
    }

    fun activateApp(packageName: String) {
        viewModelScope.launch {
            container.appRegistry.updateAppState(packageName, AppLifecycleState.ACTIVE)
            refreshApps()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun observeMonitorEvents() {
        viewModelScope.launch {
            container.remoteMonitor.let { monitor ->
                if (monitor is com.appbox.runtime.service.remote.RemoteMonitorStub) {
                    monitor.events.collect { event ->
                        _uiState.update { state ->
                            state.copy(
                                monitorEvents = (state.monitorEvents + event).takeLast(100),
                            )
                        }
                    }
                }
            }
        }
    }
}
