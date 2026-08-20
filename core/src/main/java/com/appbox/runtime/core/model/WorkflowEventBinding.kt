package com.appbox.runtime.core.model

import kotlinx.serialization.Serializable

/** Lie un topic événement à un workflow (boucle personnalisée). */
@Serializable
data class WorkflowEventBinding(
    val id: String,
    val topic: String,
    val workflowId: String,
    val label: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

object HoshiEventTopics {
    const val WHATSAPP_SENT = "hoshi.loop.whatsapp.sent"
    const val MAIL_SENT = "hoshi.loop.mail.sent"
    const val CUSTOM_PREFIX = "hoshi.loop.custom."

    val presets = listOf(
        WHATSAPP_SENT to "WhatsApp — message envoyé",
        MAIL_SENT to "Email — message envoyé",
    )
}
