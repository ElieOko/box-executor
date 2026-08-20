package com.appbox.runtime.core.model

import kotlinx.serialization.Serializable

@Serializable
data class HoshiUserConfig(
    val whatsappPhone: String = "+33600000000",
    val whatsappMessage: String = "Message automatique HOSHI à {{time}}",
    val whatsappHour: Int = 18,
    val whatsappMinute: Int = 0,
    val whatsappAutoSend: Boolean = false,
    val hnHour: Int = 8,
    val hnMinute: Int = 0,
    val voiceContinuous: Boolean = true,
    val wakeWords: List<String> = listOf("hoshi", "hey hoshi", "ok hoshi"),
)
