package com.appbox.runtime.service.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformStatusCheckerTest {

    @Test
    fun formatStatusShort_allOperational() {
        val report = PlatformStatusChecker.PlatformStatusReport(
            results = PlatformStatusChecker.DEFAULT_PLATFORMS.map {
                PlatformStatusChecker.PlatformCheckResult(
                    service = it,
                    operational = true,
                    httpStatus = 200,
                )
            },
        )
        val short = PlatformStatusChecker.formatStatusShort(report)
        assertTrue(short.contains("Tous les systèmes sont opérationnels"))
        assertTrue(short.contains("CasaNayo"))
    }

    @Test
    fun formatStatusShort_partialOutage() {
        val platforms = PlatformStatusChecker.DEFAULT_PLATFORMS
        val report = PlatformStatusChecker.PlatformStatusReport(
            results = listOf(
                PlatformStatusChecker.PlatformCheckResult(platforms[0], true, 200),
                PlatformStatusChecker.PlatformCheckResult(platforms[1], false, 503),
                PlatformStatusChecker.PlatformCheckResult(platforms[2], true, 200),
            ),
        )
        val short = PlatformStatusChecker.formatStatusShort(report)
        assertTrue(short.contains("Vehnix Auto"))
        assertTrue(short.contains("indisponible") || short.contains("Attention"))
    }

    @Test
    fun hnApiUrls_useFirebase() {
        assertEquals(
            "https://hacker-news.firebaseio.com/v0/topstories.json",
            PlatformStatusChecker.HN_TOP_STORIES_URL,
        )
    }
}
