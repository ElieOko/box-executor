package com.appbox.runtime.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackedProcess(
    val packageName: String,
    val displayName: String,
    val pid: Int,
    val uid: Int,
    val importance: Int,
    val importanceLabel: String,
    val memoryPssKb: Long = 0,
    val isForeground: Boolean = false,
    val trackedAt: Long = System.currentTimeMillis(),
)
