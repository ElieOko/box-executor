package com.appbox.runtime.service.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.appbox.runtime.core.model.ConversationTurn
import com.appbox.runtime.core.model.UserFact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.hoshiMemoryStore: DataStore<Preferences> by preferencesDataStore(name = "hoshi_memory")

class HoshiMemoryStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    private object Keys {
        val conversation = stringPreferencesKey("conversation_json")
        val facts = stringPreferencesKey("facts_json")
    }

    val conversationFlow: Flow<List<ConversationTurn>> = context.hoshiMemoryStore.data.map { prefs ->
        prefs[Keys.conversation]?.let {
            runCatching { json.decodeFromString<List<ConversationTurn>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun addUserTurn(text: String) = addTurn("user", text)

    suspend fun addAssistantTurn(text: String) = addTurn("assistant", text)

    private suspend fun addTurn(role: String, text: String) = mutex.withLock {
        val turns = loadConversation().toMutableList()
        turns += ConversationTurn(role = role, text = text.trim())
        while (turns.size > MAX_TURNS) turns.removeAt(0)
        saveConversation(turns)
    }

    suspend fun getRecentTurns(limit: Int = 10): List<ConversationTurn> =
        loadConversation().takeLast(limit)

    suspend fun rememberFact(key: String, value: String) = mutex.withLock {
        val normalizedKey = key.trim().lowercase()
        if (normalizedKey.isBlank() || value.isBlank()) return@withLock
        val factMap = loadFacts().toMutableMap()
        factMap[normalizedKey] = UserFact(key = normalizedKey, value = value.trim())
        saveFacts(factMap)
    }

    suspend fun getFacts(): Map<String, UserFact> = loadFacts()

    suspend fun buildMemoryContext(): String {
        val factList = loadFacts().values
        val recent = loadConversation().takeLast(6)
        return buildString {
            if (factList.isNotEmpty()) {
                appendLine("Faits mémorisés sur l'utilisateur:")
                factList.forEach { appendLine("- ${it.key}: ${it.value}") }
            }
            if (recent.isNotEmpty()) {
                appendLine("Historique récent:")
                recent.forEach { appendLine("- ${it.role}: ${it.text}") }
            }
        }.trim()
    }

    private suspend fun loadConversation(): List<ConversationTurn> {
        val raw = context.hoshiMemoryStore.data.map { it[Keys.conversation] }.first() ?: return emptyList()
        return runCatching { json.decodeFromString<List<ConversationTurn>>(raw) }.getOrDefault(emptyList())
    }

    private suspend fun saveConversation(turns: List<ConversationTurn>) {
        context.hoshiMemoryStore.edit { prefs ->
            prefs[Keys.conversation] = json.encodeToString(turns)
        }
    }

    private suspend fun loadFacts(): Map<String, UserFact> {
        val raw = context.hoshiMemoryStore.data.map { it[Keys.facts] }.first() ?: return emptyMap()
        return runCatching {
            json.decodeFromString<List<UserFact>>(raw).associateBy { it.key }
        }.getOrDefault(emptyMap())
    }

    private suspend fun saveFacts(facts: Map<String, UserFact>) {
        context.hoshiMemoryStore.edit { prefs ->
            prefs[Keys.facts] = json.encodeToString(facts.values.toList())
        }
    }

    companion object {
        private const val MAX_TURNS = 40
    }
}
