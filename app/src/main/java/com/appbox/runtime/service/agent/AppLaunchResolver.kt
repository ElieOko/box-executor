package com.appbox.runtime.service.agent

import com.appbox.runtime.core.config.HostAppCatalog
import com.appbox.runtime.core.model.AppBoxApp
import com.appbox.runtime.service.manager.InstalledAppCandidate
import java.text.Normalizer

/**
 * Résout une requête vocale ou textuelle vers une app du catalogue ou du système.
 */
object AppLaunchResolver {

    data class ResolvedApp(
        val packageName: String,
        val displayName: String,
        val source: Source,
    ) {
        enum class Source { CATALOG, SYSTEM, HOST_CATALOG }
    }

    fun normalizeQuery(raw: String): String {
        var text = raw.trim().lowercase()
        text = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        text = text
            .replace(Regex("^(ouvre|ouvrir|lance|lancer|demarre|démarre|open|launch)\\s+"), "")
            .replace(Regex("^(l'application|l'appli|l app|application|app)\\s+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return text
    }

    fun looksLikePackageName(value: String): Boolean =
        value.contains('.') && !value.contains(' ') && value.length > 3

    fun resolve(
        query: String,
        packageNameHint: String? = null,
        catalogApps: List<AppBoxApp>,
        systemApps: List<InstalledAppCandidate>,
    ): ResolvedApp? {
        val hint = packageNameHint?.trim()?.takeIf { it.isNotBlank() }
        if (hint != null && looksLikePackageName(hint)) {
            resolveByPackage(hint, catalogApps, systemApps)?.let { return it }
        }

        val normalized = normalizeQuery(query)
        if (normalized.isBlank()) return null

        if (looksLikePackageName(normalized)) {
            resolveByPackage(normalized, catalogApps, systemApps)?.let { return it }
        }

        resolveInList(normalized, catalogApps.map { it.packageName to it.displayName }, ResolvedApp.Source.CATALOG)?.let { return it }
        resolveInList(normalized, systemApps.map { it.packageName to it.displayName }, ResolvedApp.Source.SYSTEM)?.let { return it }

        HostAppCatalog.apps.firstOrNull { host ->
            host.displayName.lowercase() == normalized ||
                normalized.contains(host.displayName.lowercase()) ||
                host.displayName.lowercase().contains(normalized)
        }?.let {
            return ResolvedApp(it.packageName, it.displayName, ResolvedApp.Source.HOST_CATALOG)
        }

        return null
    }

    private fun resolveByPackage(
        packageName: String,
        catalogApps: List<AppBoxApp>,
        systemApps: List<InstalledAppCandidate>,
    ): ResolvedApp? {
        val pkg = packageName.lowercase()
        catalogApps.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) }?.let {
            return ResolvedApp(it.packageName, it.displayName, ResolvedApp.Source.CATALOG)
        }
        systemApps.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) }?.let {
            return ResolvedApp(it.packageName, it.displayName, ResolvedApp.Source.SYSTEM)
        }
        HostAppCatalog.find(pkg)?.let {
            return ResolvedApp(it.packageName, it.displayName, ResolvedApp.Source.HOST_CATALOG)
        }
        if (looksLikePackageName(pkg)) {
            return ResolvedApp(pkg, pkg.substringAfterLast('.'), ResolvedApp.Source.SYSTEM)
        }
        return null
    }

    private fun resolveInList(
        normalized: String,
        entries: List<Pair<String, String>>,
        source: ResolvedApp.Source,
    ): ResolvedApp? {
        entries.firstOrNull { (_, name) -> name.lowercase() == normalized }?.let { (pkg, name) ->
            return ResolvedApp(pkg, name, source)
        }
        entries.firstOrNull { (_, name) ->
            name.lowercase().contains(normalized) || normalized.contains(name.lowercase())
        }?.let { (pkg, name) ->
            return ResolvedApp(pkg, name, source)
        }
        val tokens = normalized.split(' ').filter { it.length > 2 }
        if (tokens.isNotEmpty()) {
            entries.maxByOrNull { (_, name) ->
                tokens.count { token -> name.lowercase().contains(token) }
            }?.takeIf { (_, name) ->
                tokens.any { name.lowercase().contains(it) }
            }?.let { (pkg, name) ->
                return ResolvedApp(pkg, name, source)
            }
        }
        return null
    }
}
