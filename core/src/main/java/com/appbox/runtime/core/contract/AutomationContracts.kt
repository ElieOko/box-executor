package com.appbox.runtime.core.contract

import com.appbox.runtime.core.instruction.InstructionFile
import com.appbox.runtime.core.model.AgentCommand
import com.appbox.runtime.core.model.AgentLogEntry
import com.appbox.runtime.core.model.AgentState
import com.appbox.runtime.core.model.AppBoxEvent
import com.appbox.runtime.core.model.ScheduledTask
import com.appbox.runtime.core.model.WorkflowDefinition
import com.appbox.runtime.core.model.WorkflowRun
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

interface WorkflowServiceContract {
    suspend fun registerWorkflow(definition: WorkflowDefinition): Result<Unit>
    suspend fun unregisterWorkflow(workflowId: String): Result<Unit>
    suspend fun getWorkflow(workflowId: String): WorkflowDefinition?
    suspend fun getAllWorkflows(): List<WorkflowDefinition>
    suspend fun execute(workflowId: String, initialContext: Map<String, String> = emptyMap()): Result<WorkflowRun>
    fun runs(): Flow<List<WorkflowRun>>
}

interface AgentServiceContract {
    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun loadInstructionsFromAssets(assetPath: String = "instructions/default_agent.json"): Result<InstructionFile>
    suspend fun loadInstructionsFromStorage(path: String): Result<InstructionFile>
    suspend fun applyInstructions(file: InstructionFile): Result<Unit>
    suspend fun handleCommand(command: AgentCommand): Result<WorkflowRun?>
    suspend fun handleVoice(text: String): Result<WorkflowRun?>
    suspend fun handleEvent(event: AppBoxEvent): Result<WorkflowRun?>
    fun state(): Flow<AgentState>
    fun logs(): Flow<List<AgentLogEntry>>
}

interface SchedulerServiceContract {
    suspend fun register(task: ScheduledTask): Result<Unit>
    suspend fun cancel(taskId: String): Result<Unit>
    suspend fun getAll(): List<ScheduledTask>
    suspend fun rescheduleAll(): Result<Unit>
}

interface VoiceServiceContract {
    fun startListening()
    fun stopListening()
    fun speak(text: String)
    val isListening: Boolean
    val lastTranscript: SharedFlow<String>
}
