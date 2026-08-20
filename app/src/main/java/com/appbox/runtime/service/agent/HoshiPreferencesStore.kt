package com.appbox.runtime.service.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.appbox.runtime.core.model.HoshiUserConfig
import com.appbox.runtime.core.model.ScheduleTriggerType
import com.appbox.runtime.core.model.ScheduledTask
import com.appbox.runtime.core.model.WorkflowDefinition
import com.appbox.runtime.core.model.WorkflowNodeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.hoshiPrefs: DataStore<Preferences> by preferencesDataStore(name = "hoshi_config")

class HoshiPreferencesStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val phone = stringPreferencesKey("whatsapp_phone")
        val message = stringPreferencesKey("whatsapp_message")
        val waHour = intPreferencesKey("whatsapp_hour")
        val waMinute = intPreferencesKey("whatsapp_minute")
        val waAutoSend = booleanPreferencesKey("whatsapp_auto_send")
        val hnHour = intPreferencesKey("hn_hour")
        val hnMinute = intPreferencesKey("hn_minute")
        val voiceContinuous = booleanPreferencesKey("voice_continuous")
        val wakeWords = stringPreferencesKey("wake_words_json")
    }

    val configFlow: Flow<HoshiUserConfig> = context.hoshiPrefs.data.map { prefs ->
        HoshiUserConfig(
            whatsappPhone = prefs[Keys.phone] ?: "+33600000000",
            whatsappMessage = prefs[Keys.message] ?: "Message automatique HOSHI à {{time}}",
            whatsappHour = prefs[Keys.waHour] ?: 18,
            whatsappMinute = prefs[Keys.waMinute] ?: 0,
            whatsappAutoSend = prefs[Keys.waAutoSend] ?: false,
            hnHour = prefs[Keys.hnHour] ?: 8,
            hnMinute = prefs[Keys.hnMinute] ?: 0,
            voiceContinuous = prefs[Keys.voiceContinuous] ?: true,
            wakeWords = prefs[Keys.wakeWords]?.let {
                runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(defaultWakeWords())
            } ?: defaultWakeWords(),
        )
    }

    suspend fun getConfig(): HoshiUserConfig = configFlow.first()

    suspend fun saveConfig(config: HoshiUserConfig) {
        context.hoshiPrefs.edit { prefs ->
            prefs[Keys.phone] = config.whatsappPhone
            prefs[Keys.message] = config.whatsappMessage
            prefs[Keys.waHour] = config.whatsappHour
            prefs[Keys.waMinute] = config.whatsappMinute
            prefs[Keys.waAutoSend] = config.whatsappAutoSend
            prefs[Keys.hnHour] = config.hnHour
            prefs[Keys.hnMinute] = config.hnMinute
            prefs[Keys.voiceContinuous] = config.voiceContinuous
            prefs[Keys.wakeWords] = json.encodeToString(config.wakeWords)
        }
    }

    fun applyUserConfigToWorkflows(
        workflows: List<WorkflowDefinition>,
        config: HoshiUserConfig,
    ): List<WorkflowDefinition> = workflows.map { wf ->
        when (wf.id) {
            "whatsapp_scheduled_send" -> wf.copy(
                nodes = wf.nodes.map { node ->
                    when (node.type) {
                        WorkflowNodeType.WHATSAPP_PREPARE -> node.copy(
                            config = node.config + mapOf(
                                "phone" to config.whatsappPhone,
                                "message" to config.whatsappMessage,
                            ),
                        )
                        WorkflowNodeType.WHATSAPP_OPEN -> node.copy(
                            config = node.config + mapOf(
                                "autoSend" to config.whatsappAutoSend.toString(),
                            ),
                        )
                        WorkflowNodeType.WHATSAPP_SEND_ACCESSIBILITY -> node.copy(
                            config = node.config + mapOf(
                                "enabled" to config.whatsappAutoSend.toString(),
                            ),
                        )
                        else -> node
                    }
                },
            )
            "hn_daily_digest" -> wf.copy(
                nodes = wf.nodes.map { node ->
                    if (node.type == WorkflowNodeType.HTTP_FETCH) {
                        node.copy(
                            config = node.config + mapOf(
                                "url" to "https://news.ycombinator.com/",
                                "method" to "GET",
                            ),
                        )
                    } else node
                },
            )
            else -> wf
        }
    }

    fun buildSchedules(config: HoshiUserConfig): List<ScheduledTask> = listOf(
        ScheduledTask(
            id = "whatsapp_daily",
            name = "WhatsApp HOSHI ${"%02d".format(config.whatsappHour)}:${"%02d".format(config.whatsappMinute)}",
            workflowId = "whatsapp_scheduled_send",
            triggerType = ScheduleTriggerType.DAILY_AT,
            hour = config.whatsappHour,
            minute = config.whatsappMinute,
            enabled = true,
        ),
        ScheduledTask(
            id = "hn_daily",
            name = "Hacker News HOSHI ${"%02d".format(config.hnHour)}:${"%02d".format(config.hnMinute)}",
            workflowId = "hn_daily_digest",
            triggerType = ScheduleTriggerType.DAILY_AT,
            hour = config.hnHour,
            minute = config.hnMinute,
            enabled = true,
        ),
    )

    private fun defaultWakeWords() = listOf("hoshi", "hey hoshi", "ok hoshi", "dis hoshi")
}
