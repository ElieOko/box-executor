package com.appbox.runtime.service.workflow.nodes

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.appbox.runtime.AppBoxRuntimeApplication
import com.appbox.runtime.container.AppBoxSessionActivity
import com.appbox.runtime.core.model.AppBoxEvent
import com.appbox.runtime.core.model.EventPriority
import com.appbox.runtime.core.model.RuntimeNotification
import com.appbox.runtime.core.model.WorkflowExecutionContext
import com.appbox.runtime.core.model.WorkflowNode
import com.appbox.runtime.core.model.WorkflowNodeType
import com.appbox.runtime.service.RuntimeContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class TriggerNodeExecutor : WorkflowNodeExecutor {
    override val type get() = WorkflowNodeType.TRIGGER_MANUAL
    override suspend fun execute(node: WorkflowNode, context: WorkflowExecutionContext) =
        NodeResult.Continue.also { context.log("Trigger: ${node.label}") }
}

class HttpFetchNodeExecutor : WorkflowNodeExecutor {
    override val type = WorkflowNodeType.HTTP_FETCH

    override suspend fun execute(node: WorkflowNode, context: WorkflowExecutionContext): NodeResult =
        withContext(Dispatchers.IO) {
            val url = node.config["url"] ?: return@withContext NodeResult.Fail("URL manquante")
            val method = node.config["method"] ?: "GET"
            runCatching {
                val connection = URL(context.resolve(url)).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = method
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    val body = connection.inputStream.bufferedReader().readText()
                    context.variables["http_status"] = connection.responseCode.toString()
                    context.variables["http_body"] = body
                    context.log("HTTP $method $url → ${connection.responseCode}")
                    NodeResult.Continue
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { NodeResult.Fail(it.message ?: "HTTP error") }
        }
}

class ParseHnDigestNodeExecutor : WorkflowNodeExecutor {
    override val type = WorkflowNodeType.PARSE_HN_DIGEST

    override suspend fun execute(node: WorkflowNode, context: WorkflowExecutionContext): NodeResult =
        withContext(Dispatchers.IO) {
            val limit = node.config["limit"]?.toIntOrNull() ?: 5
            val body = context.variables["http_body"] ?: return@withContext NodeResult.Fail("Pas de réponse HTTP")
            runCatching {
                val titles = if (body.contains("titleline") || body.contains("news.ycombinator.com")) {
                    parseHnHtml(body, limit)
                } else {
                    parseHnApi(body, limit)
                }
                val digest = titles.joinToString("\n")
                context.variables["digest"] = digest
                context.variables["digest_short"] = titles.take(2).joinToString(". ")
                context.variables["date"] = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
                context.variables["hn_source"] = "https://news.ycombinator.com/"
                context.log("HN digest (${titles.size} titres) depuis news.ycombinator.com")
                NodeResult.Continue
            }.getOrElse { NodeResult.Fail(it.message ?: "Parse HN error") }
        }

    private fun parseHnHtml(html: String, limit: Int): List<String> {
        val regex = Regex("""<span class="titleline"[^>]*>\s*<a[^>]*>([^<]+)</a>""")
        return regex.findAll(html).take(limit).mapIndexed { i, m ->
            "${i + 1}. ${m.groupValues[1].trim()}"
        }.toList()
    }

    private fun parseHnApi(idsJson: String, limit: Int): List<String> {
        val ids = JSONArray(idsJson)
        val titles = mutableListOf<String>()
        for (i in 0 until minOf(limit, ids.length())) {
            val id = ids.getInt(i)
            val itemUrl = URL("https://hacker-news.firebaseio.com/v0/item/$id.json")
            val conn = itemUrl.openConnection() as HttpURLConnection
            try {
                val itemBody = conn.inputStream.bufferedReader().readText()
                val title = Regex(""""title"\s*:\s*"([^"]+)"""")
                    .find(itemBody)?.groupValues?.get(1) ?: "Story $id"
                titles += "${i + 1}. $title"
            } finally {
                conn.disconnect()
            }
        }
        return titles
    }
}

class NotifyNodeExecutor(
    private val container: RuntimeContainer,
) : WorkflowNodeExecutor {
    override val type = WorkflowNodeType.NOTIFY

    override suspend fun execute(node: WorkflowNode, context: WorkflowExecutionContext): NodeResult {
        val title = context.resolve(node.config["title"] ?: "AppBox Agent")
        val body = context.resolve(node.config["body"] ?: "")
        container.notificationService.post(
            RuntimeNotification(
                id = UUID.randomUUID().toString(),
                sourcePackage = "com.appbox.runtime",
                title = title,
                body = body,
                channelId = "appbox_agent",
            ),
        )
        context.log("Notification: $title")
        return NodeResult.Continue
    }
}

class LaunchAppNodeExecutor(
    private val context: Context,
) : WorkflowNodeExecutor {
    override val type = WorkflowNodeType.LAUNCH_APP

    override suspend fun execute(node: WorkflowNode, execContext: WorkflowExecutionContext): NodeResult {
        val packageName = execContext.resolve(node.config["packageName"] ?: return NodeResult.Fail("packageName requis"))
        val displayName = execContext.resolve(node.config["displayName"] ?: packageName)
        val inBox = node.config["inBox"]?.toBooleanStrictOrNull() ?: true
        val intent = if (inBox) {
            AppBoxSessionActivity.createIntent(context, packageName, displayName)
        } else {
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return NodeResult.Fail("App non installée: $packageName")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        execContext.log("Lancement: $displayName ($packageName)")
        return NodeResult.Continue
    }
}

class WhatsAppPrepareNodeExecutor : WorkflowNodeExecutor {
    override val type = WorkflowNodeType.WHATSAPP_PREPARE

    override suspend fun execute(node: WorkflowNode, context: WorkflowExecutionContext): NodeResult {
        val phoneRaw = context.variables["whatsapp_phone"]
            ?: node.config["phone"]
            ?: ""
        val messageRaw = context.variables["whatsapp_message"]
            ?: node.config["message"]
            ?: ""
        val phone = context.resolve(phoneRaw).replace("+", "").replace(" ", "")
        val message = context.resolve(messageRaw)
        context.variables["whatsapp_phone"] = phone
        context.variables["whatsapp_message"] = message
        context.variables["time"] = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date())
        context.log("WhatsApp préparé pour $phone")
        return NodeResult.Continue
    }
}

class WhatsAppOpenNodeExecutor(
    private val context: Context,
) : WorkflowNodeExecutor {
    override val type = WorkflowNodeType.WHATSAPP_OPEN

    override suspend fun execute(node: WorkflowNode, execContext: WorkflowExecutionContext): NodeResult {
        val phone = execContext.variables["whatsapp_phone"] ?: ""
        val message = execContext.variables["whatsapp_message"] ?: ""
        val encoded = URLEncoder.encode(message, "UTF-8")
        val uri = Uri.parse("https://wa.me/$phone?text=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            val fallback = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        } else {
            context.startActivity(intent)
        }
        execContext.log("WhatsApp ouvert")
        return NodeResult.Continue
    }
}

class WhatsAppSendAccessibilityNodeExecutor : WorkflowNodeExecutor {
    override val type = WorkflowNodeType.WHATSAPP_SEND_ACCESSIBILITY

    override suspend fun execute(node: WorkflowNode, context: WorkflowExecutionContext): NodeResult {
        val autoSend = node.config["enabled"]?.toBooleanStrictOrNull()
            ?: context.variables["whatsapp_auto_send"]?.toBooleanStrictOrNull()
            ?: false
        if (!autoSend) {
            context.log("Envoi auto désactivé — validation manuelle")
            return NodeResult.Continue
        }
        delay(1500)
        return com.appbox.runtime.accessibility.HoshiAccessibilityService.sendWhatsAppMessage()
            .fold(
                onSuccess = {
                    context.log("WhatsApp envoyé via Accessibilité HOSHI")
                    NodeResult.Continue
                },
                onFailure = {
                    NodeResult.Fail(it.message ?: "Échec envoi Accessibilité")
                },
            )
    }
}

class DelayNodeExecutor : WorkflowNodeExecutor {
    override val type = WorkflowNodeType.DELAY

    override suspend fun execute(node: WorkflowNode, context: WorkflowExecutionContext): NodeResult {
        val ms = node.config["ms"]?.toLongOrNull() ?: 1000L
        delay(ms)
        context.log("Délai ${ms}ms")
        return NodeResult.Continue
    }
}

class ConditionNodeExecutor : WorkflowNodeExecutor {
    override val type = WorkflowNodeType.CONDITION

    override suspend fun execute(node: WorkflowNode, context: WorkflowExecutionContext): NodeResult {
        val key = node.config["variable"] ?: return NodeResult.Fail("variable requise")
        val expected = node.config["equals"] ?: ""
        val actual = context.variables[key] ?: ""
        val pass = actual == expected
        context.variables["condition_result"] = pass.toString()
        context.log("Condition $key == $expected → $pass")
        val branch = if (pass) node.config["onTrue"] else node.config["onFalse"]
        return branch?.let { NodeResult.Branch(it) } ?: NodeResult.Continue
    }
}

class StoreNodeExecutor(
    private val container: RuntimeContainer,
) : WorkflowNodeExecutor {
    override val type = WorkflowNodeType.STORE

    override suspend fun execute(node: WorkflowNode, context: WorkflowExecutionContext): NodeResult {
        val key = node.config["key"] ?: return NodeResult.Fail("key requise")
        val value = context.resolve(node.config["value"] ?: "")
        container.storageService.put(
            com.appbox.runtime.core.model.StorageEntry(
                key = key,
                namespace = "agent",
                ownerPackage = "com.appbox.runtime",
                value = value,
            ),
        )
        context.log("Stocké agent/$key")
        return NodeResult.Continue
    }
}

class SpeakNodeExecutor(
    private val onSpeak: (String) -> Unit,
) : WorkflowNodeExecutor {
    override val type = WorkflowNodeType.SPEAK

    override suspend fun execute(node: WorkflowNode, context: WorkflowExecutionContext): NodeResult {
        val text = context.resolve(node.config["text"] ?: "")
        if (text.isNotBlank()) onSpeak(text)
        context.log("Speak: ${text.take(60)}")
        return NodeResult.Continue
    }
}

class PublishEventNodeExecutor(
    private val container: RuntimeContainer,
) : WorkflowNodeExecutor {
    override val type = WorkflowNodeType.PUBLISH_EVENT

    override suspend fun execute(node: WorkflowNode, context: WorkflowExecutionContext): NodeResult {
        val topic = node.config["topic"] ?: "agent.event"
        val payload = context.resolve(node.config["payload"] ?: "{}")
        container.eventBus.publish(
            AppBoxEvent(
                id = UUID.randomUUID().toString(),
                topic = topic,
                sourcePackage = "com.appbox.runtime",
                payload = payload,
                priority = EventPriority.NORMAL,
            ),
        )
        context.log("Event publié: $topic")
        return NodeResult.Continue
    }
}

/** Passe-through pour nœuds trigger (déjà activés par le moteur) */
class PassThroughNodeExecutor(override val type: WorkflowNodeType) : WorkflowNodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: WorkflowExecutionContext): NodeResult {
        context.log("Pass-through ${node.type}: ${node.label}")
        return NodeResult.Continue
    }
}

fun createNodeExecutors(
    context: Context,
    container: RuntimeContainer,
    onSpeak: (String) -> Unit,
): Map<WorkflowNodeType, WorkflowNodeExecutor> {
    val passThrough = listOf(
        WorkflowNodeType.TRIGGER_SCHEDULE,
        WorkflowNodeType.TRIGGER_EVENT,
        WorkflowNodeType.TRIGGER_VOICE,
        WorkflowNodeType.TRIGGER_MANUAL,
    ).associateWith { PassThroughNodeExecutor(it) }

    return passThrough + mapOf(
        WorkflowNodeType.HTTP_FETCH to HttpFetchNodeExecutor(),
        WorkflowNodeType.PARSE_HN_DIGEST to ParseHnDigestNodeExecutor(),
        WorkflowNodeType.NOTIFY to NotifyNodeExecutor(container),
        WorkflowNodeType.LAUNCH_APP to LaunchAppNodeExecutor(context),
        WorkflowNodeType.WHATSAPP_PREPARE to WhatsAppPrepareNodeExecutor(),
        WorkflowNodeType.WHATSAPP_OPEN to WhatsAppOpenNodeExecutor(context),
        WorkflowNodeType.WHATSAPP_SEND_ACCESSIBILITY to WhatsAppSendAccessibilityNodeExecutor(),
        WorkflowNodeType.DELAY to DelayNodeExecutor(),
        WorkflowNodeType.CONDITION to ConditionNodeExecutor(),
        WorkflowNodeType.STORE to StoreNodeExecutor(container),
        WorkflowNodeType.SPEAK to SpeakNodeExecutor(onSpeak),
        WorkflowNodeType.PUBLISH_EVENT to PublishEventNodeExecutor(container),
    )
}
