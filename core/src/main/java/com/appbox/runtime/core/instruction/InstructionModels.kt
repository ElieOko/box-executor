package com.appbox.runtime.core.instruction

import com.appbox.runtime.core.model.ScheduledTask
import com.appbox.runtime.core.model.WorkflowDefinition
import kotlinx.serialization.Serializable

/**
 * Fichier d'instructions déclaratif lu par le Super Agent AppBox.
 * Format JSON — voir assets/instructions/default_agent.json
 */
@Serializable
data class InstructionFile(
    val version: Int = 1,
    val agent: AgentInstructionBlock,
)

@Serializable
data class AgentInstructionBlock(
    val name: String = "AppBox Super Agent",
    val description: String = "",
    val workflows: List<WorkflowDefinition> = emptyList(),
    val schedules: List<ScheduledTask> = emptyList(),
    val voiceCommands: List<VoiceCommandMapping> = emptyList(),
    val eventTriggers: List<EventTriggerMapping> = emptyList(),
)

@Serializable
data class VoiceCommandMapping(
    val phrase: String,
    val workflowId: String,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
data class EventTriggerMapping(
    val topic: String,
    val workflowId: String,
    val parameterMapping: Map<String, String> = emptyMap(),
)
