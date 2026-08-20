package com.appbox.runtime.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appbox.runtime.core.model.AppBoxApp
import com.appbox.runtime.core.model.AppLifecycleState
import com.appbox.runtime.core.model.RemoteMonitorEvent
import com.appbox.runtime.core.model.RuntimePermission
import com.appbox.runtime.service.RuntimeContainer
import com.appbox.runtime.service.manager.AppRegistry
import com.appbox.runtime.service.manager.InstalledAppCandidate
import com.appbox.runtime.service.manager.InstalledAppScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OsScreen {
    HOME,
    LIBRARY,
    PERMISSIONS,
    MONITOR,
    APP_PICKER,
}

data class RuntimeUiState(
    val apps: List<AppBoxApp> = emptyList(),
    val selectedApp: AppBoxApp? = null,
    val installedCandidates: List<InstalledAppCandidate> = emptyList(),
    val appPickerQuery: String = "",
    val monitorEvents: List<RemoteMonitorEvent> = emptyList(),
    val currentScreen: OsScreen = OsScreen.HOME,
    val currentTime: String = "",
    val appPermissions: Set<RuntimePermission> = emptySet(),
    val isLoadingPicker: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null,
)

class RuntimeViewModel(
    private val container: RuntimeContainer,
    private val appContext: Context,
) : ViewModel() {

    private val scanner: InstalledAppScanner = container.installedAppScanner
    private val appRegistry: AppRegistry = container.appRegistry as AppRegistry

    private val _uiState = MutableStateFlow(RuntimeUiState())
    val uiState: StateFlow<RuntimeUiState> = _uiState.asStateFlow()

    init {
        refreshApps()
        observeMonitorEvents()
        startClock()
    }

    fun refreshApps() {
        viewModelScope.launch {
            val apps = container.appRegistry.getAllApps()
            val selected = _uiState.value.selectedApp?.let { selected ->
                apps.find { it.packageName == selected.packageName }
            }
            _uiState.update {
                it.copy(
                    apps = apps,
                    selectedApp = selected,
                    error = null,
                )
            }
            selected?.let { loadPermissions(it.packageName) }
        }
    }

    fun navigateTo(screen: OsScreen) {
        _uiState.update { it.copy(currentScreen = screen, error = null) }
        if (screen == OsScreen.APP_PICKER || screen == OsScreen.LIBRARY) {
            loadInstalledCandidates()
        }
    }

    fun openAppPicker() {
        navigateTo(OsScreen.APP_PICKER)
    }

    fun closeAppPicker() {
        navigateTo(OsScreen.HOME)
    }

    fun updateAppPickerQuery(query: String) {
        _uiState.update { it.copy(appPickerQuery = query) }
    }

    fun loadInstalledCandidates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPicker = true) }
            val candidates = scanner.scanLaunchableApps()
            _uiState.update {
                it.copy(
                    installedCandidates = candidates,
                    isLoadingPicker = false,
                )
            }
        }
    }

    fun addAppFromDevice(packageName: String) {
        viewModelScope.launch {
            appRegistry.registerFromPackage(packageName)
                .onSuccess { app ->
                    _uiState.update {
                        it.copy(successMessage = "${app.displayName} ajoutée à AppBox")
                    }
                    refreshApps()
                    loadInstalledCandidates()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun removeApp(packageName: String) {
        viewModelScope.launch {
            container.appRegistry.unregisterApp(packageName)
                .onSuccess {
                    if (_uiState.value.selectedApp?.packageName == packageName) {
                        _uiState.update { it.copy(selectedApp = null, appPermissions = emptySet()) }
                    }
                    refreshApps()
                    loadInstalledCandidates()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun launchApp(packageName: String) {
        val intent = scanner.createLaunchIntent(packageName)
        if (intent != null) {
            appContext.startActivity(intent)
            viewModelScope.launch {
                container.appRegistry.updateAppState(packageName, AppLifecycleState.ACTIVE)
                container.lifecycleManager.recordHeartbeat(packageName)
                refreshApps()
            }
        } else {
            _uiState.update { it.copy(error = "Impossible de lancer cette application") }
        }
    }

    fun selectApp(app: AppBoxApp?) {
        _uiState.update { it.copy(selectedApp = app) }
        app?.let { loadPermissions(it.packageName) }
    }

    fun loadPermissions(packageName: String) {
        viewModelScope.launch {
            val permissions = container.permissionManager.getPermissions(packageName)
            _uiState.update { it.copy(appPermissions = permissions) }
        }
    }

    fun grantPermission(packageName: String, permission: RuntimePermission) {
        viewModelScope.launch {
            container.permissionManager.grant(packageName, permission)
                .onSuccess {
                    loadPermissions(packageName)
                    refreshApps()
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun revokePermission(packageName: String, permission: RuntimePermission) {
        viewModelScope.launch {
            container.permissionManager.revoke(packageName, permission)
                .onSuccess {
                    loadPermissions(packageName)
                    refreshApps()
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun suspendApp(packageName: String) {
        viewModelScope.launch {
            container.lifecycleManager.suspendApp(packageName)
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

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun filteredCandidates(): List<InstalledAppCandidate> {
        val state = _uiState.value
        val registered = state.apps.map { it.packageName }.toSet()
        val query = state.appPickerQuery.trim().lowercase()
        return state.installedCandidates.filter { candidate ->
            val matchesQuery = query.isEmpty() ||
                candidate.displayName.lowercase().contains(query) ||
                candidate.packageName.lowercase().contains(query)
            matchesQuery && candidate.packageName !in registered
        }
    }

    private fun startClock() {
        viewModelScope.launch {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            _uiState.update { it.copy(currentTime = formatter.format(Date())) }
            while (true) {
                delay(30_000)
                _uiState.update { it.copy(currentTime = formatter.format(Date())) }
            }
        }
    }

    private fun observeMonitorEvents() {
        viewModelScope.launch {
            val monitor = container.remoteMonitor
            if (monitor is com.appbox.runtime.service.remote.RemoteMonitorStub) {
                monitor.events.collect { event ->
                    _uiState.update { state ->
                        state.copy(monitorEvents = (state.monitorEvents + event).takeLast(100))
                    }
                }
            }
        }
    }
}
