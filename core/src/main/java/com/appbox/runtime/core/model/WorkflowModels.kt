package com.appbox.runtime.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class WorkflowNodeType {
    TRIGGER_SCHEDULE,
    TRIGGER_EVENT,
    TRIGGER_VOICE,
    TRIGGER_MANUAL,
    HTTP_FETCH,
    PARSE_HN_DIGEST,
    PLATFORM_STATUS_CHECK,
    NOTIFY,
    LAUNCH_APP,
    WHATSAPP_PREPARE,
    WHATSAPP_OPEN,
    WHATSAPP_SEND_ACCESSIBILITY,
    DELAY,
    CONDITION,
    STORE,
    SPEAK,
    PUBLISH_EVENT,
    UI_TAP_TEXT,
    UI_READ_SCREEN,
    UI_GLOBAL_ACTION,
    MEMORY_WRITE,
    MEMORY_READ,
    TRANSLATE_TEXT,
    WHATSAPP_BROADCAST,
    LAUNCH_APP_BY_NAME,
    OPEN_SYSTEM_APP,
}

@Serializable
data class WorkflowDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val nodes: List<WorkflowNode> = emptyList(),
    val edges: List<WorkflowEdge> = emptyList(),
)

@Serializable
data class WorkflowNode(
    val id: String,
    val type: WorkflowNodeType,
    val label: String = "",
    val config: Map<String, String> = emptyMap(),
    val positionX: Float = 0f,
    val positionY: Float = 0f,
)

@Serializable
data class WorkflowEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val label: String = "",
)

@Serializable
data class WorkflowRun(
    val id: String,
    val workflowId: String,
    val status: WorkflowRunStatus,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val currentNodeId: String? = null,
    val context: Map<String, String> = emptyMap(),
    val logs: List<String> = emptyList(),
    val error: String? = null,
)

@Serializable
enum class WorkflowRunStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Serializable
data class WorkflowExecutionContext(
    val variables: MutableMap<String, String> = mutableMapOf(),
    val logs: MutableList<String> = mutableListOf(),
) {
    fun log(message: String) {
        logs += "[${System.currentTimeMillis()}] $message"
    }

    fun resolve(template: String): String {
        var result = template
        variables.forEach { (key, value) ->
            result = result.replace("{{$key}}", value)
        }
        return result
    }
}
