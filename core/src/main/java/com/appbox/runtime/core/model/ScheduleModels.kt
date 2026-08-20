package com.appbox.runtime.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ScheduleTriggerType {
    ONE_SHOT,
    DAILY_AT,
    INTERVAL,
}

@Serializable
data class ScheduledTask(
    val id: String,
    val name: String,
    val workflowId: String,
    val triggerType: ScheduleTriggerType,
    /** ONE_SHOT: epoch ms */
    val atEpochMs: Long = 0,
    /** DAILY_AT / ONE_SHOT time parts */
    val hour: Int = 9,
    val minute: Int = 0,
    /** INTERVAL: repeat every N ms */
    val intervalMs: Long = 86_400_000,
    val enabled: Boolean = true,
    val lastRunAt: Long? = null,
    val nextRunAt: Long? = null,
)
