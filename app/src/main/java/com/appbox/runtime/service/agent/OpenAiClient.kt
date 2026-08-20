package com.appbox.runtime.service.agent

import com.appbox.runtime.core.model.HoshiUserConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Client OpenAI partagé — traduction, personnalisation, génération */
class OpenAiClient {

    suspend fun translateToFrench(
        text: String,
        config: HoshiUserConfig,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (config.openAiApiKey.isBlank()) return@withContext Result.success(text)
        runCatching {
            val prompt = """
                Traduis ce texte en français naturel. Garde la numérotation et la structure.
                Réponds UNIQUEMENT avec la traduction, sans commentaire.
                
                $text
            """.trimIndent()
            chat(config, prompt, temperature = 0.2, maxTokens = 800)
        }
    }

    suspend fun personalizeMessage(
        template: String,
        contactName: String,
        groupName: String,
        config: HoshiUserConfig,
        userHint: String = "",
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!config.whatsappPersonalizeWithAi || config.openAiApiKey.isBlank()) {
            return@withContext Result.success(
                template
                    .replace("{{name}}", contactName)
                    .replace("{{group}}", groupName)
                    .replace("{{time}}", java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE)
                        .format(java.util.Date())),
            )
        }
        runCatching {
            val prompt = """
                Personnalise ce message WhatsApp pour $contactName (groupe: $groupName).
                Template: $template
                ${if (userHint.isNotBlank()) "Consigne utilisateur: $userHint" else ""}
                Réponds UNIQUEMENT avec le message final en français, concis et naturel.
            """.trimIndent()
            chat(config, prompt, temperature = 0.6, maxTokens = 200)
        }
    }

    suspend fun answerConversational(
        question: String,
        config: HoshiUserConfig,
        memoryContext: String = "",
        appsContext: String = "",
    ): Result<String> = withContext(Dispatchers.IO) {
        if (config.openAiApiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("OpenAI non configuré"))
        }
        runCatching {
            val system = buildString {
                append(JarvisPersona.systemPromptPrefix(config))
                appendLine()
                if (config.techExpertMode) {
                    appendLine("Expert en programmation, architecture, cloud native, Kubernetes, DevOps.")
                }
                appendLine("Réponds en français, concis pour TTS (2-4 phrases sauf question technique).")
                if (memoryContext.isNotBlank()) appendLine("Mémoire: $memoryContext")
                if (appsContext.isNotBlank()) appendLine("Apps disponibles: $appsContext")
            }
            chat(config, question, systemPrompt = system, temperature = 0.5, maxTokens = 600)
        }
    }

    suspend fun chat(
        config: HoshiUserConfig,
        userPrompt: String,
        systemPrompt: String = "Tu es HOSHI, assistant expert.",
        temperature: Double = 0.4,
        maxTokens: Int = 500,
    ): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", userPrompt))
        val body = JSONObject()
            .put("model", config.openAiModel.ifBlank { "gpt-4o-mini" })
            .put("temperature", temperature)
            .put("max_tokens", maxTokens)
            .put("messages", messages)
            .toString()
        return post(config.openAiApiKey, body).trim()
    }

    private fun post(apiKey: String, body: String): String {
        val connection = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 25_000
            connection.readTimeout = 45_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { it.write(body) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().readText()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("OpenAI ${connection.responseCode}: ${response.take(150)}")
            }
            val root = JSONObject(response)
            return root.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        } finally {
            connection.disconnect()
        }
    }
}
