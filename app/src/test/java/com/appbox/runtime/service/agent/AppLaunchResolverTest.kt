package com.appbox.runtime.service.agent

import com.appbox.runtime.core.model.AppBoxApp
import com.appbox.runtime.service.manager.InstalledAppCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppLaunchResolverTest {

    private val catalog = listOf(
        AppBoxApp(
            packageName = "com.yvent.app",
            displayName = "Yvent",
            versionName = "1.0",
            versionCode = 1,
            signatureHash = "test",
        ),
    )
    private val system = listOf(
        InstalledAppCandidate("com.chrome.android", "Chrome", "1", 1, false),
    )

    @Test
    fun resolve_byPackageName() {
        val resolved = AppLaunchResolver.resolve("yvent", "com.yvent.app", catalog, system)
        assertNotNull(resolved)
        assertEquals("com.yvent.app", resolved!!.packageName)
    }

    @Test
    fun resolve_byDisplayName() {
        val resolved = AppLaunchResolver.resolve("ouvre yvent", null, catalog, system)
            ?: AppLaunchResolver.resolve(AppLaunchResolver.normalizeQuery("ouvre yvent"), null, catalog, system)
        assertNotNull(resolved)
        assertEquals("Yvent", resolved!!.displayName)
    }

    @Test
    fun normalizeQuery_stripsVerbs() {
        assertEquals("yvent", AppLaunchResolver.normalizeQuery("ouvre l'application yvent"))
    }
}
