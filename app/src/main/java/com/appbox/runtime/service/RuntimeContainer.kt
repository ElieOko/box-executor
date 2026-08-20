package com.appbox.runtime.service

import android.app.Application
import com.appbox.runtime.core.contract.AgentServiceContract
import com.appbox.runtime.core.contract.SchedulerServiceContract
import com.appbox.runtime.core.contract.VoiceServiceContract
import com.appbox.runtime.core.contract.WorkflowServiceContract
import com.appbox.runtime.core.event.InProcessEventBus
import com.appbox.runtime.service.agent.AutomationAgent
import com.appbox.runtime.service.scheduler.SchedulerService
import com.appbox.runtime.service.voice.VoiceService
import com.appbox.runtime.service.workflow.WorkflowEngine
import kotlinx.coroutines.runBlocking

class RuntimeContainer(application: Application) {
    val eventBus = InProcessEventBus()
    val remoteMonitor: com.appbox.runtime.core.contract.RemoteMonitorContract =
        com.appbox.runtime.service.remote.RemoteMonitorStub()

    val appRegistry: com.appbox.runtime.core.contract.AppRegistryContract =
        com.appbox.runtime.service.manager.AppRegistry(application, remoteMonitor)
    val installedAppScanner = com.appbox.runtime.service.manager.InstalledAppScanner(application)
    val permissionManager: com.appbox.runtime.core.contract.PermissionContract =
        com.appbox.runtime.service.manager.PermissionManager(appRegistry, remoteMonitor)
    val authService: com.appbox.runtime.core.contract.AuthServiceContract =
        com.appbox.runtime.service.auth.AuthService(permissionManager, remoteMonitor)
    val storageService: com.appbox.runtime.core.contract.StorageServiceContract =
        com.appbox.runtime.service.storage.StorageService(application, permissionManager)
    val networkService: com.appbox.runtime.core.contract.NetworkServiceContract =
        com.appbox.runtime.service.network.NetworkService(permissionManager, remoteMonitor)
    val notificationService: com.appbox.runtime.core.contract.NotificationServiceContract =
        com.appbox.runtime.service.notification.NotificationService(application, permissionManager)
    val lifecycleManager = com.appbox.runtime.service.manager.LifecycleManager(appRegistry, remoteMonitor)
    val eventBusContract: com.appbox.runtime.core.contract.EventBusContract =
        RuntimeEventBus(eventBus, permissionManager)

    lateinit var voiceService: VoiceServiceContract
        private set

    lateinit var workflowEngine: WorkflowEngine
        private set

    lateinit var scheduler: SchedulerService
        private set

    lateinit var automationAgent: AutomationAgent
        private set

    val workflowService: WorkflowServiceContract get() = workflowEngine
    val agentService: AgentServiceContract get() = automationAgent
    val schedulerService: SchedulerServiceContract get() = scheduler

    private var speakCallback: (String) -> Unit = {}

    val hoshiPreferences = com.appbox.runtime.service.agent.HoshiPreferencesStore(application)
    val hoshiMemory = com.appbox.runtime.service.agent.HoshiMemoryStore(application)
    val contactGroups = com.appbox.runtime.service.agent.ContactGroupStore(application)
    val eventBindingStore = com.appbox.runtime.service.workflow.WorkflowEventBindingStore(application)
    val openAiClient = com.appbox.runtime.service.agent.OpenAiClient()

    init {
        workflowEngine = WorkflowEngine(
            context = application,
            container = this,
            onSpeak = { text -> speakCallback(text) },
        )
        scheduler = SchedulerService(application) { task ->
            automationAgent.onScheduleTriggered(task.id, task.workflowId)
        }
        automationAgent = AutomationAgent(
            context = application,
            workflowEngine = workflowEngine,
            scheduler = scheduler,
            preferences = hoshiPreferences,
            memory = hoshiMemory,
            contactGroups = contactGroups,
            eventBindings = eventBindingStore,
            installedAppScanner = installedAppScanner,
            appRegistry = appRegistry,
            openAiClient = openAiClient,
            onSpeak = { text -> speakCallback(text) },
        )
        voiceService = VoiceService(application) { transcript ->
            automationAgent.handleVoice(transcript)
        }
        speakCallback = { text -> voiceService.speak(text) }
    }

    fun initializeAutomation() {
        runBlocking {
            workflowEngine.initialize()
            scheduler.initialize()
            automationAgent.start()
            automationAgent.subscribeToEvents(eventBus)
            voiceService.startContinuousListening()
        }
    }
}
