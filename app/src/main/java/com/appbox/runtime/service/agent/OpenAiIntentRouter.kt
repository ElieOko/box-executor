package com.appbox.runtime.service.agent

import com.appbox.runtime.core.instruction.VoiceCommandMapping
import com.appbox.runtime.core.model.ConversationTurn
import com.appbox.runtime.core.model.HoshiUserConfig
import com.appbox.runtime.core.model.OpenAiIntentResult
import com.appbox.runtime.core.model.WorkflowDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OpenAiIntentRouter(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun resolveIntent(
        userText: String,
        config: HoshiUserConfig,
        workflows: List<WorkflowDefinition>,
        voiceCommands: List<VoiceCommandMapping>,
        memoryContext: String = "",
        recentTurns: List<ConversationTurn> = emptyList(),
    ): Result<OpenAiIntentResult> = withContext(Dispatchers.IO) {
        val apiKey = config.openAiApiKey.trim()
        if (apiKey.isBlank() || !config.openAiEnabled) {
            return@withContext Result.failure(IllegalStateException("OpenAI non configuré"))
        }
        runCatching {
            val body = buildRequestBody(userText, config, workflows, voiceCommands, memoryContext, recentTurns)
            val responseText = postChatCompletion(apiKey, body)
            parseIntentResponse(responseText)
        }
    }

    internal fun buildRequestBody(
        userText: String,
        config: HoshiUserConfig,
        workflows: List<WorkflowDefinition>,
        voiceCommands: List<VoiceCommandMapping>,
        memoryContext: String,
        recentTurns: List<ConversationTurn>,
    ): String {
        val time = SimpleDateFormat("EEEE dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
        val workflowList = workflows.joinToString("\n") { wf ->
            "- ${wf.id}: ${wf.name} — ${wf.description}"
        }
        val commandList = voiceCommands.joinToString("\n") { cmd ->
            "- \"${cmd.phrase}\" → ${cmd.workflowId}"
        }
        val persona = JarvisPersona.systemPromptPrefix(config)
        val systemPrompt = """
            $persona
            Analyse la demande et réponds UNIQUEMENT en JSON valide.

            Workflows disponibles:
            $workflowList

            Exemples de commandes:
            $commandList

            Config:
            - WhatsApp: ${config.whatsappPhone}, message: ${config.whatsappMessage}
            - Heure WhatsApp: ${config.whatsappHour}:${"%02d".format(config.whatsappMinute)}
            - Briefing matinal: ${config.morningBriefingHour}:${"%02d".format(config.morningBriefingMinute)}
            - Maintenant: $time

            ${if (memoryContext.isNotBlank()) "Mémoire:\n$memoryContext" else ""}

            Schéma JSON strict:
            {
              "action": "workflow" | "speak" | "remember" | "unknown",
              "workflow_id": "id ou null",
              "parameters": { "cle": "valeur" },
              "speak": "réponse TTS en français"
            }

            Règles:
            - workflow: exécuter une automatisation (workflow_id requis)
            - speak: conversation, statut, clarification
            - remember: mémoriser un fait (parameters: fact_key, fact_value) — ex. préférence utilisateur
            - unknown: hors périmètre
            - speak concis, ton JARVIS si mode actif
        """.trimIndent()

        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt))
        recentTurns.forEach { turn ->
            val role = if (turn.role == "assistant") "assistant" else "user"
            messages.put(JSONObject().put("role", role).put("content", turn.text))
        }
        messages.put(JSONObject().put("role", "user").put("content", userText))

        return JSONObject()
            .put("model", config.openAiModel.ifBlank { "gpt-4o-mini" })
            .put("temperature", if (config.jarvisMode) 0.35 else 0.2)
            .put("max_tokens", 450)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", messages)
            .toString()
    }

    internal fun parseIntentResponse(raw: String): OpenAiIntentResult {
        val content = extractAssistantContent(raw)
        return parseIntentContent(content)
    }

    internal fun parseIntentContent(content: String): OpenAiIntentResult {
        return json.decodeFromString<OpenAiIntentResult>(content.trim())
    }

    private fun extractAssistantContent(responseBody: String): String {
        val root = JSONObject(responseBody)
        val choices = root.getJSONArray("choices")
        if (choices.length() == 0) throw IllegalStateException("Réponse OpenAI vide")
        return choices.getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    private fun postChatCompletion(apiKey: String, body: String): String {
        val connection = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { it.write(body) }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val response = stream.bufferedReader().readText()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("OpenAI HTTP ${connection.responseCode}: ${response.take(200)}")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
