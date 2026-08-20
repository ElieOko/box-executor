package com.appbox.runtime.service.agent

object JarvisPersona {

    fun startupGreeting(
        config: com.appbox.runtime.core.model.HoshiUserConfig,
        platformStatusShort: String? = null,
    ): String {
        val title = addressTitle(config)
        val status = platformStatusShort?.takeIf { it.isNotBlank() }
            ?: "Tous les systèmes sont opérationnels."
        return if (config.jarvisMode) {
            "Bonjour $title. $status Je suis HOSHI, votre assistant. Que puis-je faire pour vous ?"
        } else {
            "Bonjour, je suis HOSHI. $status Je vous écoute."
        }
    }

    fun scheduleAck(config: com.appbox.runtime.core.model.HoshiUserConfig, taskName: String): String {
        val title = addressTitle(config)
        return if (config.jarvisMode) {
            "Très bien $title. J'exécute $taskName."
        } else {
            "Exécution planifiée."
        }
    }

    fun wakeAck(config: com.appbox.runtime.core.model.HoshiUserConfig): String {
        val title = addressTitle(config)
        return if (config.jarvisMode) "Oui $title, je vous écoute." else "Oui, je vous écoute."
    }

    fun errorMessage(config: com.appbox.runtime.core.model.HoshiUserConfig): String {
        val title = addressTitle(config)
        return if (config.jarvisMode) {
            "Mes excuses $title, une anomalie s'est produite."
        } else {
            "Désolé, une erreur s'est produite."
        }
    }

    fun addressTitle(config: com.appbox.runtime.core.model.HoshiUserConfig): String {
        val name = config.userName.trim()
        val title = config.userTitle.trim().ifBlank { "Monsieur" }
        return if (name.isNotBlank()) "$title $name" else title
    }

    fun systemPromptPrefix(config: com.appbox.runtime.core.model.HoshiUserConfig): String {
        val title = addressTitle(config)
        return if (config.jarvisMode) {
            """
            Tu es HOSHI, l'assistant intelligent d'AppBox — inspiré de JARVIS (Iron Man).
            Tu t'adresses à l'utilisateur par « $title ».
            Ton : calme, professionnel, efficace, légèrement formel, jamais familier.
            Réponses courtes et précises, comme un majordome technologique.
            Tu anticipes les besoins et confirmes avant d'agir.
            """.trimIndent()
        } else {
            "Tu es HOSHI, agent vocal français sur Android."
        }
    }
}
