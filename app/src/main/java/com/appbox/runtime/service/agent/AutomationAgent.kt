package com.appbox.runtime.service.agent

import android.content.Context
import com.appbox.runtime.core.contract.AgentServiceContract
import com.appbox.runtime.core.instruction.InstructionFile
import com.appbox.runtime.core.instruction.InstructionParser
import com.appbox.runtime.core.instruction.ReactiveExpressionMapping
import com.appbox.runtime.core.model.AgentCommand
import com.appbox.runtime.core.model.AgentCommandSource
import com.appbox.runtime.core.model.AgentLogEntry
import com.appbox.runtime.core.model.AgentLogLevel
import com.appbox.runtime.core.model.AgentState
import com.appbox.runtime.core.model.AgentStatus
import com.appbox.runtime.core.model.AppBoxEvent
import com.appbox.runtime.core.model.HoshiUserConfig
import com.appbox.runtime.core.model.WorkflowRun
import com.appbox.runtime.service.scheduler.SchedulerService
import com.appbox.runtime.service.workflow.WorkflowEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AutomationAgent(
    private val context: Context,
    val workflowEngine: WorkflowEngine,
    val scheduler: SchedulerService,
    private val preferences: HoshiPreferencesStore,
    private val memory: HoshiMemoryStore,
    private val contactGroups: ContactGroupStore,
    private val installedAppScanner: com.appbox.runtime.service.manager.InstalledAppScanner,
    private val appRegistry: com.appbox.runtime.core.contract.AppRegistryContract,
    private val openAiClient: OpenAiClient = OpenAiClient(),
    private val openAiRouter: OpenAiIntentRouter = OpenAiIntentRouter(),
    private val onSpeak: (String) -> Unit,
) : AgentServiceContract {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private var instructions: InstructionFile? = null
    private var userConfig: HoshiUserConfig = HoshiUserConfig()
    private val logs = mutableListOf<AgentLogEntry>()
    private val _state = MutableStateFlow(AgentState(name = "HOSHI"))
    override fun state(): Flow<AgentState> = _state.asStateFlow()
    override fun logs(): Flow<List<AgentLogEntry>> = _logsFlow.asStateFlow()
    private val _logsFlow = MutableStateFlow<List<AgentLogEntry>>(emptyList())

    fun conversationFlow(): Flow<List<com.appbox.runtime.core.model.ConversationTurn>> = memory.conversationFlow

    private var eventJobStarted = false

    override suspend fun start(): Result<Unit> = runCatching {
        userConfig = preferences.getConfig()
        com.appbox.runtime.container.LockTaskManager.enabled = userConfig.lockTaskEnabled
        loadInstructionsFromAssets().getOrThrow()
        applyInstructions(instructions!!).getOrThrow()
        val platformReport = PlatformStatusChecker.checkAll()
        val platformShort = PlatformStatusChecker.formatStatusShort(platformReport)
        val greeting = JarvisPersona.startupGreeting(userConfig, platformShort)
        speakAndRemember(greeting)
        log(AgentLogLevel.INFO, "HOSHI démarré (mode JARVIS=${userConfig.jarvisMode})")
        com.appbox.runtime.service.overlay.HoshiFloatingOverlayService.show(context)
        _state.value = _state.value.copy(status = AgentStatus.IDLE, updatedAt = System.currentTimeMillis())
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        _state.value = _state.value.copy(status = AgentStatus.IDLE)
    }

    suspend fun refreshUserConfig(): Result<Unit> = runCatching {
        userConfig = preferences.getConfig()
        instructions?.let { applyInstructions(it) }?.getOrThrow()
    }

    suspend fun saveUserConfig(config: HoshiUserConfig): Result<Unit> = runCatching {
        preferences.saveConfig(config)
        userConfig = config
        com.appbox.runtime.container.LockTaskManager.enabled = config.lockTaskEnabled
        instructions?.let { applyInstructions(it) }?.getOrThrow()
    }

    suspend fun updateWorkflow(definition: com.appbox.runtime.core.model.WorkflowDefinition): Result<Unit> =
        workflowEngine.registerWorkflow(definition)

    override suspend fun loadInstructionsFromAssets(
        assetPath: String,
    ): Result<InstructionFile> = runCatching {
        val content = context.assets.open(assetPath).bufferedReader().readText()
        val file = InstructionParser.parse(content).getOrThrow()
        instructions = file.copy(agent = file.agent.copy(name = "HOSHI"))
        _state.value = _state.value.copy(
            name = "HOSHI",
            lastInstructionSource = assetPath,
            updatedAt = System.currentTimeMillis(),
        )
        file
    }

    override suspend fun loadInstructionsFromStorage(path: String): Result<InstructionFile> = runCatching {
        val file = java.io.File(path)
        val content = file.readText()
        InstructionParser.parse(content).getOrThrow().also {
            instructions = it
            _state.value = _state.value.copy(name = "HOSHI", lastInstructionSource = path)
        }
    }

    override suspend fun applyInstructions(file: InstructionFile): Result<Unit> = mutex.withLock {
        runCatching {
            instructions = file
            val workflows = preferences.applyUserConfigToWorkflows(file.agent.workflows, userConfig)
            workflows.forEach { workflowEngine.registerWorkflow(it).getOrThrow() }
            preferences.buildSchedules(userConfig).forEach { scheduler.register(it).getOrThrow() }
            _state.value = _state.value.copy(
                name = "HOSHI",
                pendingTasks = preferences.buildSchedules(userConfig).count { it.enabled },
                status = AgentStatus.WAITING_SCHEDULE,
                updatedAt = System.currentTimeMillis(),
            )
            log(AgentLogLevel.INFO, "${workflows.size} workflows HOSHI actifs")
        }
    }

    override suspend fun handleCommand(command: AgentCommand): Result<WorkflowRun?> {
        _state.value = _state.value.copy(
            status = AgentStatus.EXECUTING,
            activeWorkflowId = command.action,
            updatedAt = System.currentTimeMillis(),
        )
        log(AgentLogLevel.INFO, "Commande ${command.source}: ${command.action}", command.action)
        val params = command.parameters.toMutableMap()
        params["time"] = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date())
        params["whatsapp_phone"] = userConfig.whatsappPhone
        params["whatsapp_message"] = userConfig.whatsappMessage
        params["whatsapp_auto_send"] = userConfig.whatsappAutoSend.toString()
        params["user_title"] = if (userConfig.jarvisMode) {
            JarvisPersona.addressTitle(userConfig)
        } else {
            userConfig.userTitle
        }
        params["user_name"] = userConfig.userName
        return workflowEngine.execute(command.action, params).map { run ->
            _state.value = _state.value.copy(status = AgentStatus.IDLE, activeWorkflowId = null)
            run
        }.onFailure {
            _state.value = _state.value.copy(status = AgentStatus.ERROR)
            log(AgentLogLevel.ERROR, it.message ?: "Erreur commande")
            speakAndRemember(JarvisPersona.errorMessage(userConfig))
        }
    }

    override suspend fun handleVoice(text: String): Result<WorkflowRun?> {
        val file = instructions ?: return Result.failure(IllegalStateException("Instructions non chargées"))
        val normalized = normalizeVoiceInput(text)
        val corrected = VoiceInputCorrector.correct(normalized)
        if (corrected.isBlank()) {
            if (text.contains("hoshi")) {
                speakAndRemember(JarvisPersona.wakeAck(userConfig))
            }
            _state.value = _state.value.copy(status = AgentStatus.IDLE)
            return Result.success(null)
        }

        if (userConfig.memoryEnabled) {
            memory.addUserTurn(normalized)
        }

        _state.value = _state.value.copy(
            status = AgentStatus.LISTENING,
            lastVoiceCommand = corrected,
            updatedAt = System.currentTimeMillis(),
        )

        if (userConfig.openAiEnabled && userConfig.openAiApiKey.isNotBlank()) {
            resolveWithOpenAi(file, corrected)?.let { return it }
        }

        return handleVoiceWithRules(file, corrected)
    }

    private suspend fun buildAppsContext(): String {
        val catalog = appRegistry.getAllApps().joinToString(", ") { "${it.displayName} [box]" }
        val installed = installedAppScanner.scanLaunchableApps().take(40).joinToString(", ") { it.displayName }
        return "Catalogue AppBox: $catalog | Système: $installed"
    }

    private suspend fun resolveWithOpenAi(
        file: InstructionFile,
        corrected: String,
    ): Result<WorkflowRun?>? {
        val memoryContext = if (userConfig.memoryEnabled) memory.buildMemoryContext() else ""
        val recentTurns = if (userConfig.memoryEnabled) memory.getRecentTurns(8) else emptyList()
        val catalogCtx = appRegistry.getAllApps().joinToString("\n") { "- ${it.displayName} → ${it.packageName} (AppBox)" }

        return openAiRouter.resolveIntentRobust(
            userText = corrected,
            config = userConfig,
            workflows = file.agent.workflows,
            voiceCommands = file.agent.voiceCommands,
            memoryContext = memoryContext,
            recentTurns = recentTurns,
            contactGroupsContext = contactGroups.buildContextForLlm(contactGroups.getGroups()),
            installedAppsContext = installedAppScanner.scanLaunchableApps().take(50).joinToString(", ") { it.displayName },
            catalogAppsContext = catalogCtx,
        ).fold(
            onSuccess = { intent ->
                when {
                    intent.action == "unknown" -> answerDirectly(corrected)
                    else -> processOpenAiIntent(intent, corrected)
                }
            },
            onFailure = { error ->
                log(AgentLogLevel.ERROR, "OpenAI: ${error.message}")
                answerDirectly(corrected)
            },
        )
    }

    private suspend fun answerDirectly(question: String): Result<WorkflowRun?>? {
        val appName = VoiceInputCorrector.extractAppName(question)
        if (appName != null) {
            val catalogMatch = appRegistry.getAllApps().firstOrNull {
                it.displayName.lowercase().contains(appName) || appName.contains(it.displayName.lowercase())
            }
            if (catalogMatch != null) {
                return handleCommand(
                    AgentCommand(
                        id = UUID.randomUUID().toString(),
                        source = AgentCommandSource.VOICE,
                        action = "launch_appbox_catalog",
                        parameters = mapOf("app_name" to catalogMatch.displayName),
                    ),
                )
            }
        }
        return openAiClient.answerConversational(
            question = question,
            config = userConfig,
            memoryContext = if (userConfig.memoryEnabled) memory.buildMemoryContext() else "",
            appsContext = buildAppsContext() + " | " + PlatformStatusChecker.platformsContextForLlm(),
        ).fold(
            onSuccess = { answer ->
                speakAndRemember(answer)
                _state.value = _state.value.copy(status = AgentStatus.IDLE)
                Result.success(null)
            },
            onFailure = { error ->
                log(AgentLogLevel.ERROR, "Réponse OpenAI: ${error.message}")
                val fallback = if (userConfig.jarvisMode) {
                    "Je n'ai pas pu contacter le modèle, ${JarvisPersona.addressTitle(userConfig)}. Vérifiez la clé OpenAI ou reformulez une commande simple."
                } else {
                    "Impossible de joindre OpenAI. Vérifiez la configuration ou dites une commande HOSHI."
                }
                speakAndRemember(fallback)
                _state.value = _state.value.copy(status = AgentStatus.IDLE)
                Result.success(null)
            },
        )
    }

    private suspend fun processOpenAiIntent(
        intent: com.appbox.runtime.core.model.OpenAiIntentResult,
        originalText: String,
    ): Result<WorkflowRun?>? {
        log(AgentLogLevel.INFO, "OpenAI → ${intent.action} ${intent.workflowId ?: ""}")
        return when {
            intent.isRememberAction() -> {
                val key = intent.parameters["fact_key"] ?: intent.parameters["key"] ?: ""
                val value = intent.parameters["fact_value"] ?: intent.parameters["value"] ?: ""
                if (key.isNotBlank() && value.isNotBlank() && userConfig.memoryEnabled) {
                    memory.rememberFact(key, value)
                }
                val reply = intent.speak ?: "C'est mémorisé, ${JarvisPersona.addressTitle(userConfig)}."
                speakAndRemember(reply)
                _state.value = _state.value.copy(status = AgentStatus.IDLE)
                Result.success(null)
            }
            intent.isWorkflowAction() -> {
                var params = intent.parameters.toMutableMap()
                if (params["app_name"].isNullOrBlank()) {
                    VoiceInputCorrector.extractAppName(originalText)?.let { params["app_name"] = it }
                }
                handleCommand(
                    AgentCommand(
                        id = UUID.randomUUID().toString(),
                        source = AgentCommandSource.VOICE,
                        action = intent.workflowId!!,
                        parameters = params,
                    ),
                )
            }
            intent.isSpeakOnly() -> {
                speakAndRemember(intent.speak!!)
                _state.value = _state.value.copy(status = AgentStatus.IDLE)
                Result.success(null)
            }
            else -> null
        }
    }

    private suspend fun handleVoiceWithRules(
        file: InstructionFile,
        normalized: String,
    ): Result<WorkflowRun?> {
        matchReactiveExpression(file.agent.reactiveExpressions, normalized)?.let { reactive ->
            reactive.response?.let { speakAndRemember(it) }
            reactive.workflowId?.let { wfId ->
                return handleCommand(
                    AgentCommand(
                        id = UUID.randomUUID().toString(),
                        source = AgentCommandSource.VOICE,
                        action = wfId,
                        parameters = reactive.parameters,
                    ),
                )
            }
            _state.value = _state.value.copy(status = AgentStatus.IDLE)
            return Result.success(null)
        }

        val mapping = file.agent.voiceCommands.firstOrNull { cmd ->
            normalized.contains(cmd.phrase.lowercase(Locale.FRANCE))
        } ?: run {
            val fallback = if (userConfig.jarvisMode) {
                "Je n'ai pas saisi votre demande, ${JarvisPersona.addressTitle(userConfig)}. Reformulez, s'il vous plaît."
            } else {
                "Je n'ai pas compris. Dites par exemple : HOSHI, envoie WhatsApp."
            }
            speakAndRemember(fallback)
            _state.value = _state.value.copy(status = AgentStatus.IDLE)
            return Result.success(null)
        }

        return handleCommand(
            AgentCommand(
                id = UUID.randomUUID().toString(),
                source = AgentCommandSource.VOICE,
                action = mapping.workflowId,
                parameters = mapping.parameters,
            ),
        )
    }

    override suspend fun handleEvent(event: AppBoxEvent): Result<WorkflowRun?> {
        val file = instructions ?: return Result.success(null)
        val mapping = file.agent.eventTriggers.firstOrNull { it.topic == event.topic }
            ?: return Result.success(null)

        val params = mapping.parameterMapping.toMutableMap()
        params["event_payload"] = event.payload
        params["event_topic"] = event.topic

        return handleCommand(
            AgentCommand(
                id = UUID.randomUUID().toString(),
                source = AgentCommandSource.EVENT_BUS,
                action = mapping.workflowId,
                parameters = params,
            ),
        )
    }

    suspend fun onScheduleTriggered(taskId: String, workflowId: String): Result<WorkflowRun?> {
        scheduler.markRun(taskId)
        log(AgentLogLevel.INFO, "Planification $taskId → $workflowId", workflowId)
        val taskName = when (taskId) {
            "jarvis_morning" -> "votre briefing matinal"
            "whatsapp_daily" -> "l'envoi WhatsApp"
            "hn_daily" -> "le digest Hacker News"
            else -> "la tâche planifiée"
        }
        speakAndRemember(JarvisPersona.scheduleAck(userConfig, taskName))
        return handleCommand(
            AgentCommand(
                id = UUID.randomUUID().toString(),
                source = AgentCommandSource.SCHEDULE,
                action = workflowId,
                parameters = mapOf("scheduleId" to taskId),
            ),
        )
    }

    suspend fun runWorkflowManually(workflowId: String, params: Map<String, String> = emptyMap()): Result<WorkflowRun?> =
        handleCommand(
            AgentCommand(
                id = UUID.randomUUID().toString(),
                source = AgentCommandSource.UI,
                action = workflowId,
                parameters = params,
            ),
        )

    fun subscribeToEvents(eventBus: com.appbox.runtime.core.event.InProcessEventBus) {
        if (eventJobStarted) return
        eventJobStarted = true
        scope.launch {
            eventBus.events.collect { event ->
                if (event.topic.startsWith("agent.") || event.topic.startsWith("hoshi.") ||
                    event.sourcePackage == "com.appbox.runtime"
                ) {
                    handleEvent(event)
                }
            }
        }
    }

    private suspend fun speakAndRemember(text: String) {
        if (userConfig.memoryEnabled) {
            memory.addAssistantTurn(text)
        }
        onSpeak(text)
    }

    private fun normalizeVoiceInput(text: String): String {
        var result = text.trim().lowercase(Locale.FRANCE)
        userConfig.wakeWords.forEach { wake ->
            result = result.replace(wake.lowercase(Locale.FRANCE), "").trim()
        }
        result = result.replace(Regex("^[,\\s]+"), "")
        return result.trim()
    }

    private fun matchReactiveExpression(
        expressions: List<ReactiveExpressionMapping>,
        text: String,
    ): ReactiveExpressionMapping? {
        return expressions.firstOrNull { expr ->
            expr.patterns.any { pattern -> text.contains(pattern.lowercase(Locale.FRANCE)) }
        }
    }

    private fun log(level: AgentLogLevel, message: String, workflowId: String? = null) {
        val entry = AgentLogEntry(
            id = UUID.randomUUID().toString(),
            level = level,
            message = message,
            workflowId = workflowId,
        )
        logs += entry
        if (logs.size > 100) logs.removeAt(0)
        _logsFlow.value = logs.toList()
    }
}
