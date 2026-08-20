package com.appbox.runtime.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appbox.runtime.container.AppBoxSessionActivity
import com.appbox.runtime.container.LockTaskManager
import com.appbox.runtime.container.ProcessTracker
import com.appbox.runtime.service.ProcessWatchdogService
import com.appbox.runtime.service.ReturnOverlayService
import com.appbox.runtime.service.RuntimeContainer
import com.appbox.runtime.service.manager.AppRegistry
import com.appbox.runtime.service.manager.InstalledAppCandidate
import com.appbox.runtime.service.manager.InstalledAppScanner
import com.appbox.runtime.core.model.AppBoxApp
import com.appbox.runtime.core.model.AppLifecycleState
import com.appbox.runtime.core.model.RemoteMonitorEvent
import com.appbox.runtime.core.model.RuntimePermission
import com.appbox.runtime.core.model.AgentLogEntry
import com.appbox.runtime.core.model.AgentState
import com.appbox.runtime.core.model.ScheduledTask
import com.appbox.runtime.core.model.WorkflowDefinition
import com.appbox.runtime.core.model.WorkflowRun
import com.appbox.runtime.core.model.TrackedProcess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OsScreen {
    HOME,
    LIBRARY,
    PERMISSIONS,
    MONITOR,
    AGENT,
    APP_PICKER,
}

data class RuntimeUiState(
    val apps: List<AppBoxApp> = emptyList(),
    val selectedApp: AppBoxApp? = null,
    val installedCandidates: List<InstalledAppCandidate> = emptyList(),
    val appPickerQuery: String = "",
    val monitorEvents: List<RemoteMonitorEvent> = emptyList(),
    val trackedProcesses: List<TrackedProcess> = emptyList(),
    val hasUsageAccess: Boolean = false,
    val canDrawOverlay: Boolean = false,
    val isLockTaskActive: Boolean = false,
    val isDeviceOwner: Boolean = false,
    val environmentActive: Boolean = true,
    val currentScreen: OsScreen = OsScreen.HOME,
    val appPermissions: Set<RuntimePermission> = emptySet(),
    val isLoadingPicker: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null,
    val agentState: AgentState = AgentState(),
    val workflows: List<WorkflowDefinition> = emptyList(),
    val selectedWorkflow: WorkflowDefinition? = null,
    val schedules: List<ScheduledTask> = emptyList(),
    val workflowRuns: List<WorkflowRun> = emptyList(),
    val agentLogs: List<AgentLogEntry> = emptyList(),
    val isVoiceListening: Boolean = false,
    val lastVoiceText: String? = null,
)

class RuntimeViewModel(
    private val container: RuntimeContainer,
    private val appContext: Context,
) : ViewModel() {

    private val scanner: InstalledAppScanner = container.installedAppScanner
    private val appRegistry: AppRegistry = container.appRegistry as AppRegistry
    private val processTracker = ProcessTracker(appContext)

    private val _uiState = MutableStateFlow(RuntimeUiState())
    val uiState: StateFlow<RuntimeUiState> = _uiState.asStateFlow()

    init {
        refreshApps()
        refreshEnvironmentState()
        observeMonitorEvents()
        observeProcesses()
        registerCatalogApps()
        observeAgent()
    }

    fun refreshEnvironmentState() {
        _uiState.update {
            it.copy(
                hasUsageAccess = processTracker.hasUsageAccess(),
                canDrawOverlay = ReturnOverlayService.canDrawOverlays(appContext),
                isLockTaskActive = LockTaskManager.isInLockTask(appContext),
                isDeviceOwner = LockTaskManager.isDeviceOwner(appContext),
            )
        }
    }

    fun enterEnvironment(activity: Activity) {
        LockTaskManager.enterLockTask(activity)
        LockTaskManager.syncWhitelist(
            activity,
            _uiState.value.apps.map { it.packageName },
        )
        ProcessWatchdogService.start(activity)
        _uiState.update { it.copy(environmentActive = true, isLockTaskActive = true) }
        refreshEnvironmentState()
    }

    fun exitEnvironment(activity: Activity) {
        LockTaskManager.exitLockTask(activity)
        ReturnOverlayService.hide(appContext)
        _uiState.update { it.copy(environmentActive = false, isLockTaskActive = false) }
        refreshEnvironmentState()
    }

    fun openUsageAccessSettings() {
        appContext.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openOverlaySettings() {
        ReturnOverlayService.openOverlaySettings(appContext)
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
            LockTaskManager.syncWhitelist(appContext, apps.map { it.packageName })
        }
    }

    fun navigateTo(screen: OsScreen) {
        _uiState.update { it.copy(currentScreen = screen, error = null) }
        if (screen == OsScreen.APP_PICKER || screen == OsScreen.LIBRARY) {
            loadInstalledCandidates()
        }
        if (screen == OsScreen.AGENT) {
            refreshAgentData()
        }
    }

    fun openAppPicker() = navigateTo(OsScreen.APP_PICKER)

    fun closeAppPicker() = navigateTo(OsScreen.HOME)

    fun updateAppPickerQuery(query: String) {
        _uiState.update { it.copy(appPickerQuery = query) }
    }

    fun loadInstalledCandidates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPicker = true) }
            val candidates = scanner.scanLaunchableApps()
            _uiState.update {
                it.copy(installedCandidates = candidates, isLoadingPicker = false)
            }
        }
    }

    fun addAppFromDevice(packageName: String) {
        viewModelScope.launch {
            appRegistry.registerFromPackage(packageName.trim())
                .onSuccess { app ->
                    LockTaskManager.syncWhitelist(appContext, appRegistry.getAllApps().map { it.packageName })
                    _uiState.update {
                        it.copy(successMessage = "${app.displayName} ajoutée à AppBox")
                    }
                    refreshApps()
                    loadInstalledCandidates()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Impossible d'ajouter l'application") }
                }
        }
    }

    fun registerCatalogApps() {
        viewModelScope.launch {
            val added = appRegistry.ensureBundledHostApps()
            if (added.isNotEmpty()) {
                _uiState.update {
                    it.copy(successMessage = "${added.size} app(s) hôte(s) enregistrée(s)")
                }
            }
            LockTaskManager.syncWhitelist(appContext, appRegistry.getAllApps().map { it.packageName })
            refreshApps()
        }
    }

    fun removeApp(packageName: String) {
        viewModelScope.launch {
            processTracker.stopProcess(packageName)
            container.appRegistry.unregisterApp(packageName)
                .onSuccess {
                    if (_uiState.value.selectedApp?.packageName == packageName) {
                        _uiState.update { it.copy(selectedApp = null, appPermissions = emptySet()) }
                    }
                    refreshApps()
                    loadInstalledCandidates()
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun launchAppInBox(packageName: String) {
        val app = _uiState.value.apps.find { it.packageName == packageName }
        val displayName = app?.displayName ?: packageName
        val intent = AppBoxSessionActivity.createIntent(appContext, packageName, displayName)
        appContext.startActivity(intent)
        viewModelScope.launch {
            container.appRegistry.updateAppState(packageName, AppLifecycleState.ACTIVE)
            container.lifecycleManager.recordHeartbeat(packageName)
            refreshApps()
        }
    }

    fun refreshUsageAccess() {
        _uiState.update { it.copy(hasUsageAccess = processTracker.hasUsageAccess()) }
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

    private fun observeProcesses() {
        viewModelScope.launch {
            ProcessWatchdogService.processes.collect { processes ->
                _uiState.update { it.copy(trackedProcesses = processes) }
            }
        }
    }

    private fun observeAgent() {
        viewModelScope.launch {
            container.automationAgent.state().collect { state ->
                _uiState.update { it.copy(agentState = state) }
            }
        }
        viewModelScope.launch {
            container.automationAgent.logs().collect { logs ->
                _uiState.update { it.copy(agentLogs = logs) }
            }
        }
        viewModelScope.launch {
            container.workflowEngine.runs().collect { runs ->
                _uiState.update { it.copy(workflowRuns = runs) }
            }
        }
        viewModelScope.launch {
            container.voiceService.lastTranscript.collect { text ->
                _uiState.update { it.copy(lastVoiceText = text) }
            }
        }
        refreshAgentData()
    }

    fun refreshAgentData() {
        viewModelScope.launch {
            val workflows = container.workflowEngine.getAllWorkflows()
            _uiState.update { state ->
                state.copy(
                    workflows = workflows,
                    schedules = container.scheduler.getAll(),
                    selectedWorkflow = state.selectedWorkflow ?: workflows.firstOrNull(),
                )
            }
        }
    }

    fun selectWorkflow(workflow: WorkflowDefinition) {
        _uiState.update { it.copy(selectedWorkflow = workflow) }
    }

    fun runWorkflow(workflowId: String) {
        viewModelScope.launch {
            container.automationAgent.runWorkflowManually(workflowId)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Workflow $workflowId exécuté") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Échec workflow") }
                }
        }
    }

    fun startVoiceListening() {
        container.voiceService.startListening()
        _uiState.update { it.copy(isVoiceListening = true) }
    }

    fun stopVoiceListening() {
        container.voiceService.stopListening()
        _uiState.update { it.copy(isVoiceListening = false) }
    }

    fun reloadAgentInstructions() {
        viewModelScope.launch {
            container.automationAgent.loadInstructionsFromAssets()
                .onSuccess { file ->
                    container.automationAgent.applyInstructions(file)
                    refreshAgentData()
                    _uiState.update { it.copy(successMessage = "Instructions agent rechargées") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Erreur rechargement") }
                }
        }
    }
}
