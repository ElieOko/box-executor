package com.appbox.runtime.service.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiIntentRouterTest {

    private val router = OpenAiIntentRouter()

    @Test
    fun parseIntentResponse_workflowAction() {
        val content = """
            {
              "action": "workflow",
              "workflow_id": "whatsapp_scheduled_send",
              "parameters": { "immediate": "true" },
              "speak": "J'envoie WhatsApp."
            }
        """.trimIndent()
        val result = router.parseIntentContent(content)

        assertTrue(result.isWorkflowAction())
        assertEquals("whatsapp_scheduled_send", result.workflowId)
        assertEquals("true", result.parameters["immediate"])
        assertEquals("J'envoie WhatsApp.", result.speak)
    }

    @Test
    fun parseIntentResponse_speakOnly() {
        val content = """{"action":"speak","workflow_id":null,"parameters":{},"speak":"Bonjour !"}"""
        val result = router.parseIntentContent(content)

        assertTrue(result.isSpeakOnly())
        assertEquals("Bonjour !", result.speak)
    }
}
