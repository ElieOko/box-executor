package com.appbox.runtime.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationTurn(
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class UserFact(
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
