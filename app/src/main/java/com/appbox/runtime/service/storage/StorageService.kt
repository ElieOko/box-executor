package com.appbox.runtime.service.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.appbox.runtime.core.contract.PermissionContract
import com.appbox.runtime.core.contract.StorageServiceContract
import com.appbox.runtime.core.model.RuntimePermission
import com.appbox.runtime.core.model.StorageEntry
import com.appbox.runtime.core.security.RuntimeConstants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.runtimeStorage: DataStore<Preferences> by preferencesDataStore(
    name = "appbox_runtime_storage",
)

class StorageService(
    private val context: Context,
    private val permissions: PermissionContract,
) : StorageServiceContract {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val dataStore = context.runtimeStorage

    override suspend fun put(entry: StorageEntry): Result<Unit> = mutex.withLock {
        runCatching {
            if (entry.ownerPackage != RuntimeConstants.RUNTIME_PACKAGE &&
                !permissions.hasPermission(entry.ownerPackage, RuntimePermission.STORAGE_WRITE)
            ) {
                throw SecurityException("STORAGE_WRITE not granted for ${entry.ownerPackage}")
            }
            val key = storageKey(entry.namespace, entry.key)
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey(key)] = json.encodeToString(entry)
            }
            Unit
        }
    }

    override suspend fun get(
        namespace: String,
        key: String,
        requesterPackage: String,
    ): StorageEntry? = mutex.withLock {
        if (!canRead(namespace, requesterPackage)) return null
        val storageKey = storageKey(namespace, key)
        val jsonStr = dataStore.data.map { prefs ->
            prefs[stringPreferencesKey(storageKey)]
        }.first() ?: return null
        json.decodeFromString<StorageEntry>(jsonStr)
    }

    override suspend fun delete(
        namespace: String,
        key: String,
        requesterPackage: String,
    ): Result<Unit> = mutex.withLock {
        runCatching {
            val entry = get(namespace, key, requesterPackage)
                ?: throw IllegalArgumentException("Entry not found")
            if (entry.ownerPackage != requesterPackage &&
                !permissions.hasPermission(requesterPackage, RuntimePermission.STORAGE_WRITE)
            ) {
                throw SecurityException("Cannot delete entry owned by ${entry.ownerPackage}")
            }
            val storageKey = storageKey(namespace, key)
            dataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey(storageKey))
            }
            Unit
        }
    }

    override suspend fun list(namespace: String, requesterPackage: String): List<StorageEntry> {
        if (!canRead(namespace, requesterPackage)) return emptyList()
        val prefix = "$namespace:"
        val allPrefs = dataStore.data.first()
        return allPrefs.asMap().mapNotNull { (prefKey, value) ->
            if (!prefKey.name.startsWith(prefix)) return@mapNotNull null
            try {
                val entry = json.decodeFromString<StorageEntry>(value as String)
                if (namespace == RuntimeConstants.SYSTEM_NAMESPACE &&
                    entry.ownerPackage != requesterPackage
                ) {
                    null
                } else {
                    entry
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun canRead(namespace: String, requesterPackage: String): Boolean {
        if (namespace == RuntimeConstants.SYSTEM_NAMESPACE) {
            return permissions.hasPermission(requesterPackage, RuntimePermission.STORAGE_READ)
        }
        return permissions.hasPermission(requesterPackage, RuntimePermission.STORAGE_READ)
    }

    private fun storageKey(namespace: String, key: String) = "$namespace:$key"
}
