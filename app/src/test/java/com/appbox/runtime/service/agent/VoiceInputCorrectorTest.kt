package com.appbox.runtime.service.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInputCorrectorTest {

    @Test
    fun correct_whatsappTypos() {
        assertEquals("whatsapp", VoiceInputCorrector.correct("whats app"))
        assertEquals("envoie whatsapp", VoiceInputCorrector.correct("envoi whatsapp"))
    }

    @Test
    fun extractAppName_frenchVerbs() {
        assertEquals("yvent", VoiceInputCorrector.extractAppName("ouvre yvent"))
        assertEquals("chrome", VoiceInputCorrector.extractAppName("lance chrome"))
    }
}
