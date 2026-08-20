package com.appbox.runtime.core.model

import kotlinx.serialization.Serializable

@Serializable
data class HoshiUserConfig(
    val whatsappPhone: String = "+33600000000",
    val whatsappMessage: String = "Message automatique HOSHI à {{time}}",
    val whatsappHour: Int = 18,
    val whatsappMinute: Int = 0,
    val whatsappAutoSend: Boolean = true,
    val hnHour: Int = 8,
    val hnMinute: Int = 0,
    val voiceContinuous: Boolean = true,
    val wakeWords: List<String> = listOf("hoshi", "hey hoshi", "ok hoshi"),
    /** Clé OpenAI — stockée localement, jamais commitée */
    val openAiApiKey: String = "",
    val openAiModel: String = "gpt-4o-mini",
    val openAiEnabled: Boolean = true,
    /** Mode JARVIS — personnalité, proactivité, mémoire */
    val jarvisMode: Boolean = true,
    val userTitle: String = "Monsieur",
    val userName: String = "",
    val proactiveEnabled: Boolean = true,
    val morningBriefingHour: Int = 8,
    val morningBriefingMinute: Int = 0,
    val memoryEnabled: Boolean = true,
    val ttsSpeechRate: Float = 0.88f,
    val ttsPitch: Float = 0.95f,
    /** Nom de voix TTS Android (vide = auto meilleure voix FR) */
    val ttsVoiceName: String = "",
    /** STT — réduction bruit ambiant */
    val sttSilenceMs: Long = 2200L,
    val sttNoiseFilterEnabled: Boolean = true,
    val sttMinConfidence: Float = 0.45f,
    val sttMinSpeechMs: Long = 400L,
    /** Traduction automatique des news anglaises */
    val translateNewsToFrench: Boolean = true,
    /** Expertise programmation / cloud / K8s dans OpenAI */
    val techExpertMode: Boolean = true,
    /** Personnaliser messages WhatsApp via IA par contact */
    val whatsappPersonalizeWithAi: Boolean = true,
    val defaultContactGroupId: String = "",
)
