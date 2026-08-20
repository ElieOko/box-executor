package com.appbox.runtime.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAiIntentResult(
    val action: String = "unknown",
    @SerialName("workflow_id") val workflowId: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val speak: String? = null,
) {
    fun isWorkflowAction(): Boolean = action == "workflow" && !workflowId.isNullOrBlank()

    fun isSpeakOnly(): Boolean = action == "speak" && !speak.isNullOrBlank()

    fun isRememberAction(): Boolean = action == "remember" &&
        (!parameters["fact_key"].isNullOrBlank() || !parameters["key"].isNullOrBlank())
}
