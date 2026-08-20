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
        contactGroupsContext: String = "",
        installedAppsContext: String = "",
    ): Result<OpenAiIntentResult> = withContext(Dispatchers.IO) {
        val apiKey = config.openAiApiKey.trim()
        if (apiKey.isBlank() || !config.openAiEnabled) {
            return@withContext Result.failure(IllegalStateException("OpenAI non configuré"))
        }
        runCatching {
            val body = buildRequestBody(
                userText, config, workflows, voiceCommands, memoryContext, recentTurns,
                contactGroupsContext, installedAppsContext,
            )
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
        contactGroupsContext: String,
        installedAppsContext: String,
    ): String {
        val time = SimpleDateFormat("EEEE dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
        val workflowList = workflows.joinToString("\n") { wf ->
            "- ${wf.id}: ${wf.name} — ${wf.description}"
        }
        val commandList = voiceCommands.joinToString("\n") { cmd ->
            "- \"${cmd.phrase}\" → ${cmd.workflowId}"
        }
        val persona = JarvisPersona.systemPromptPrefix(config)
        val techExpertise = if (config.techExpertMode) """
            Expertise technique (niveau architecte senior):
            Programmation, architecture logicielle, cloud native, Kubernetes, DevOps, CI/CD.
            Pour questions tech: action=speak avec réponse précise et structurée.
        """.trimIndent() else ""
        val maxTokens = if (config.techExpertMode && looksLikeTechQuestion(userText)) 700 else 450

        val systemPrompt = """
            $persona
            $techExpertise
            Analyse la demande et réponds UNIQUEMENT en JSON valide.

            Workflows disponibles:
            $workflowList

            Exemples de commandes:
            $commandList

            Applications installées:
            $installedAppsContext

            Groupes contacts WhatsApp:
            $contactGroupsContext

            Config:
            - WhatsApp: ${config.whatsappPhone}, message: ${config.whatsappMessage}
            - Heure WhatsApp: ${config.whatsappHour}:${"%02d".format(config.whatsappMinute)}
            - Briefing: ${config.morningBriefingHour}:${"%02d".format(config.morningBriefingMinute)}
            - Groupe défaut: ${config.defaultContactGroupId.ifBlank { "aucun" }}
            - Maintenant: $time

            ${if (memoryContext.isNotBlank()) "Mémoire:\n$memoryContext" else ""}

            Schéma JSON:
            { "action": "workflow"|"speak"|"remember"|"unknown", "workflow_id": "...", "parameters": {}, "speak": "..." }

            Règles:
            - whatsapp_group_broadcast: params contact_group_id, message_hint
            - launch_app_by_name: params app_name (Instagram, Chrome, etc.)
            - open_system_app: params app_target (settings, wifi)
            - speak: conversation et questions tech détaillées
            - remember: fact_key, fact_value
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
            .put("max_tokens", maxTokens)
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

    private fun looksLikeTechQuestion(text: String): Boolean {
        val keywords = listOf(
            "kubernetes", "k8s", "docker", "cloud", "programm", "code", "architecture",
            "microservice", "api", "kotlin", "java", "python", "terraform", "helm",
            "devops", "gitops", "cluster", "pod", "deploy",
        )
        val lower = text.lowercase()
        return keywords.any { lower.contains(it) }
    }
}
