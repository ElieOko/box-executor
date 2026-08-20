package com.appbox.runtime.service.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.appbox.runtime.core.model.HoshiContact
import com.appbox.runtime.core.model.HoshiContactGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.contactGroupsStore: DataStore<Preferences> by preferencesDataStore(name = "hoshi_contacts")

class ContactGroupStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    private object Keys {
        val groupsJson = stringPreferencesKey("contact_groups_json")
    }

    val groupsFlow: Flow<List<HoshiContactGroup>> = context.contactGroupsStore.data.map { prefs ->
        prefs[Keys.groupsJson]?.let {
            runCatching { json.decodeFromString<List<HoshiContactGroup>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun getGroups(): List<HoshiContactGroup> = groupsFlow.first()

    suspend fun getGroup(groupId: String): HoshiContactGroup? =
        getGroups().firstOrNull { it.id == groupId }

    suspend fun saveGroups(groups: List<HoshiContactGroup>) = mutex.withLock {
        context.contactGroupsStore.edit { prefs ->
            prefs[Keys.groupsJson] = json.encodeToString(groups)
        }
    }

    suspend fun upsertGroup(group: HoshiContactGroup) {
        val updated = getGroups().filter { it.id != group.id } + group
        saveGroups(updated)
    }

    suspend fun deleteGroup(groupId: String) {
        saveGroups(getGroups().filter { it.id != groupId })
    }

    fun buildContextForLlm(groups: List<HoshiContactGroup>): String {
        if (groups.isEmpty()) return "Aucun groupe de contacts configuré."
        return groups.joinToString("\n") { group ->
            val contacts = group.contacts.joinToString(", ") { "${it.name} (${it.phone})" }
            "- ${group.id}: ${group.name} [${contacts}] template: ${group.messageTemplate}"
        }
    }

    companion object {
        fun newGroup(name: String): HoshiContactGroup = HoshiContactGroup(
            id = "grp_${UUID.randomUUID().toString().take(8)}",
            name = name,
        )

        fun newContact(name: String, phone: String): HoshiContact = HoshiContact(
            id = "ct_${UUID.randomUUID().toString().take(8)}",
            name = name,
            phone = phone,
        )
    }
}
