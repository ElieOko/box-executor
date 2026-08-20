package com.appbox.runtime.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentState(
    val name: String = "AppBox Super Agent",
    val status: AgentStatus = AgentStatus.IDLE,
    val lastInstructionSource: String? = null,
    val activeWorkflowId: String? = null,
    val lastVoiceCommand: String? = null,
    val pendingTasks: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class AgentStatus {
    IDLE,
    LISTENING,
    EXECUTING,
    WAITING_SCHEDULE,
    ERROR,
}

@Serializable
data class AgentCommand(
    val id: String,
    val source: AgentCommandSource,
    val action: String,
    val parameters: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
enum class AgentCommandSource {
    INSTRUCTION_FILE,
    EVENT_BUS,
    VOICE,
    UI,
    SCHEDULE,
}

@Serializable
data class AgentLogEntry(
    val id: String,
    val level: AgentLogLevel,
    val message: String,
    val workflowId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
enum class AgentLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}
