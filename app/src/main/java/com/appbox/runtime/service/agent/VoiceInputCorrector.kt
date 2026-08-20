package com.appbox.runtime.service.agent

/**
 * Corrige les erreurs courantes de reconnaissance vocale avant envoi au modèle.
 */
object VoiceInputCorrector {

    private val REPLACEMENTS = listOf(
        "whats app" to "whatsapp",
        "what's app" to "whatsapp",
        "watsap" to "whatsapp",
        "ouvre l'appli" to "ouvre",
        "ouvrez" to "ouvre",
        "envoi" to "envoie",
        "groupe whatsapp" to "groupe",
        "hacker new" to "hacker news",
        "kubernetes" to "kubernetes",
        "cube netes" to "kubernetes",
        "kubernetis" to "kubernetes",
        "briefing du matin" to "briefing",
        "lis moi l'écran" to "lis l'écran",
        "que voit tu" to "que vois-tu",
        "ouvre moi" to "ouvre",
        "lance moi" to "lance",
        "lance l'application" to "lance",
        "ouvre l'application" to "ouvre",
        "parametre" to "paramètres",
        "parametres" to "paramètres",
        "actualite" to "actualités",
        "envoyer un message" to "envoie whatsapp",
        "message whatsapp" to "envoie whatsapp",
    )

    fun correct(raw: String): String {
        var text = raw.trim().lowercase()
        REPLACEMENTS.forEach { (wrong, right) ->
            text = text.replace(wrong, right)
        }
        text = text.replace(Regex("\\s+"), " ").trim()
        return text
    }

    /** Extrait le nom d'app après « ouvre » / « lance » */
    fun extractAppName(text: String): String? {
        val patterns = listOf(
            Regex("(?:ouvre|ouvrir|lance|lancer|démarre|demarre)\\s+(.+)"),
            Regex("(?:open|launch)\\s+(.+)"),
        )
        for (pattern in patterns) {
            pattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.let {
                if (it.isNotBlank()) return it
            }
        }
        return null
    }
}
