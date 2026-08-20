package com.appbox.runtime.core.model

import kotlinx.serialization.Serializable

@Serializable
data class HoshiContact(
    val id: String,
    val name: String,
    val phone: String,
)

@Serializable
data class HoshiContactGroup(
    val id: String,
    val name: String,
    val contacts: List<HoshiContact> = emptyList(),
    /** Modèle de message — variables: {{name}}, {{time}}, {{group}} */
    val messageTemplate: String = "Bonjour {{name}}, message de HOSHI à {{time}}.",
)
