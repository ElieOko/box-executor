package com.appbox.runtime.service.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Vérifie la disponibilité des plateformes CasaNayo, Vehnix Auto et Barua Officiel.
 */
object PlatformStatusChecker {

    data class PlatformService(
        val id: String,
        val name: String,
        val url: String,
        val description: String,
    )

    data class PlatformCheckResult(
        val service: PlatformService,
        val operational: Boolean,
        val httpStatus: Int?,
        val error: String? = null,
    )

    data class PlatformStatusReport(
        val results: List<PlatformCheckResult>,
    ) {
        val allOperational: Boolean get() = results.isNotEmpty() && results.all { it.operational }
        val operationalCount: Int get() = results.count { it.operational }
    }

    val DEFAULT_PLATFORMS = listOf(
        PlatformService(
            id = "casanayo",
            name = "CasaNayo",
            url = "https://casanayo.com/",
            description = "Immobilier et services à domicile en RDC",
        ),
        PlatformService(
            id = "vehnix",
            name = "Vehnix Auto",
            url = "https://www.vehnixauto.com/",
            description = "Pièces, véhicules et motos en RDC",
        ),
        PlatformService(
            id = "barua",
            name = "Barua Officiel",
            url = "https://baruaofficiel.com/",
            description = "Services de l'État",
        ),
    )

    /** URL officielle API Hacker News temps réel (Firebase) */
    const val HN_TOP_STORIES_URL = "https://hacker-news.firebaseio.com/v0/topstories.json"
    const val HN_ITEM_URL_TEMPLATE = "https://hacker-news.firebaseio.com/v0/item/%d.json"

    suspend fun checkAll(
        platforms: List<PlatformService> = DEFAULT_PLATFORMS,
    ): PlatformStatusReport = withContext(Dispatchers.IO) {
        PlatformStatusReport(platforms.map { checkPlatform(it) })
    }

    fun checkPlatform(service: PlatformService): PlatformCheckResult = runCatching {
        val connection = URL(service.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            val code = connection.responseCode
            PlatformCheckResult(
                service = service,
                operational = code in 200..399,
                httpStatus = code,
            )
        } finally {
            connection.disconnect()
        }
    }.getOrElse { e ->
        PlatformCheckResult(
            service = service,
            operational = false,
            httpStatus = null,
            error = e.message?.take(80),
        )
    }

    fun formatStatusLong(report: PlatformStatusReport): String = buildString {
        report.results.forEach { r ->
            val status = if (r.operational) "opérationnel" else "indisponible"
            appendLine("• ${r.service.name} (${r.service.url}) — $status")
        }
        if (report.allOperational) {
            appendLine("Tous les systèmes sont opérationnels.")
        }
    }.trim()

    fun formatStatusShort(report: PlatformStatusReport): String {
        if (report.allOperational) {
            val names = report.results.joinToString(", ") { it.service.name }
            return "Tous les systèmes sont opérationnels. $names répondent correctement."
        }
        val down = report.results.filter { !it.operational }.joinToString(", ") { it.service.name }
        val up = report.results.filter { it.operational }.joinToString(", ") { it.service.name }
        return buildString {
            if (up.isNotBlank()) append("Opérationnels : $up. ")
            append("Attention : $down semble indisponible.")
        }.trim()
    }

    fun platformsContextForLlm(): String = DEFAULT_PLATFORMS.joinToString("\n") {
        "- ${it.name} (${it.url}) : ${it.description}"
    }
}
