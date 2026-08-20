package com.appbox.runtime.service.agent

import android.content.Context
import com.appbox.runtime.core.contract.AgentServiceContract
import com.appbox.runtime.core.instruction.InstructionFile
import com.appbox.runtime.core.instruction.InstructionParser
import com.appbox.runtime.core.model.AgentCommand
import com.appbox.runtime.core.model.AgentCommandSource
import com.appbox.runtime.core.model.AgentLogEntry
import com.appbox.runtime.core.model.AgentLogLevel
import com.appbox.runtime.core.model.AgentState
import com.appbox.runtime.core.model.AgentStatus
import com.appbox.runtime.core.model.AppBoxEvent
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
import java.util.UUID

class AutomationAgent(
    private val context: Context,
    val workflowEngine: WorkflowEngine,
    val scheduler: SchedulerService,
    private val onSpeak: (String) -> Unit,
) : AgentServiceContract {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private var instructions: InstructionFile? = null
    private val logs = mutableListOf<AgentLogEntry>()
    private val _state = MutableStateFlow(AgentState())
    override fun state(): Flow<AgentState> = _state.asStateFlow()
    override fun logs(): Flow<List<AgentLogEntry>> = _logsFlow.asStateFlow()
    private val _logsFlow = MutableStateFlow<List<AgentLogEntry>>(emptyList())

    private var eventJobStarted = false

    override suspend fun start(): Result<Unit> = runCatching {
        loadInstructionsFromAssets().getOrThrow()
        applyInstructions(instructions!!).getOrThrow()
        log(AgentLogLevel.INFO, "Super Agent démarré")
        _state.value = _state.value.copy(status = AgentStatus.IDLE, updatedAt = System.currentTimeMillis())
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        _state.value = _state.value.copy(status = AgentStatus.IDLE)
    }

    override suspend fun loadInstructionsFromAssets(
        assetPath: String,
    ): Result<InstructionFile> = runCatching {
        val content = context.assets.open(assetPath).bufferedReader().readText()
        val file = InstructionParser.parse(content).getOrThrow()
        instructions = file
        _state.value = _state.value.copy(
            name = file.agent.name,
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
            _state.value = _state.value.copy(
                lastInstructionSource = path,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun applyInstructions(file: InstructionFile): Result<Unit> = mutex.withLock {
        runCatching {
            instructions = file
            file.agent.workflows.forEach { workflowEngine.registerWorkflow(it).getOrThrow() }
            file.agent.schedules.forEach { scheduler.register(it).getOrThrow() }
            _state.value = _state.value.copy(
                name = file.agent.name,
                pendingTasks = file.agent.schedules.count { it.enabled },
                status = AgentStatus.WAITING_SCHEDULE,
                updatedAt = System.currentTimeMillis(),
            )
            log(AgentLogLevel.INFO, "${file.agent.workflows.size} workflows, ${file.agent.schedules.size} planifications")
        }
    }

    override suspend fun handleCommand(command: AgentCommand): Result<WorkflowRun?> {
        _state.value = _state.value.copy(
            status = AgentStatus.EXECUTING,
            activeWorkflowId = command.action,
            updatedAt = System.currentTimeMillis(),
        )
        log(AgentLogLevel.INFO, "Commande ${command.source}: ${command.action}", command.action)
        return workflowEngine.execute(command.action, command.parameters).map { run ->
            _state.value = _state.value.copy(
                status = AgentStatus.IDLE,
                activeWorkflowId = null,
                updatedAt = System.currentTimeMillis(),
            )
            run
        }.onFailure {
            _state.value = _state.value.copy(status = AgentStatus.ERROR)
            log(AgentLogLevel.ERROR, it.message ?: "Erreur commande")
        }
    }

    override suspend fun handleVoice(text: String): Result<WorkflowRun?> {
        val file = instructions ?: return Result.failure(IllegalStateException("Instructions non chargées"))
        _state.value = _state.value.copy(
            status = AgentStatus.LISTENING,
            lastVoiceCommand = text,
            updatedAt = System.currentTimeMillis(),
        )

        val mapping = file.agent.voiceCommands.firstOrNull { cmd ->
            text.contains(cmd.phrase.lowercase())
        } ?: run {
            onSpeak("Commande non reconnue")
            _state.value = _state.value.copy(status = AgentStatus.IDLE)
            return Result.success(null)
        }

        onSpeak("Exécution de ${mapping.workflowId}")
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
                if (event.topic.startsWith("agent.") || event.sourcePackage == "com.appbox.runtime") {
                    handleEvent(event)
                }
            }
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
