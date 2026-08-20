package com.appbox.runtime.core.security

import com.appbox.runtime.core.model.RuntimePermission
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureVerifierTest {

  @Test
  fun trustedApp_matchesPackageAndSignature() {
        val trusted = listOf(
            TrustedApp("com.example.app", "abc123", "Example App"),
        )
        assertTrue(SignatureVerifier.isTrusted("com.example.app", "abc123", trusted))
        assertFalse(SignatureVerifier.isTrusted("com.example.app", "wrong", trusted))
        assertFalse(SignatureVerifier.isTrusted("com.other.app", "abc123", trusted))
    }

    @Test
    fun runtimePermission_hasExpectedCount() {
        assertTrue(RuntimePermission.entries.size >= 10)
    }
}
