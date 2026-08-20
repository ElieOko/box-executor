package com.appbox.runtime.core.instruction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionParserTest {

    @Test
    fun parseDefaultStructure() {
        val json = """
            {
              "version": 1,
              "agent": {
                "name": "Test Agent",
                "workflows": [
                  {
                    "id": "wf1",
                    "name": "Test Flow",
                    "nodes": [],
                    "edges": []
                  }
                ],
                "schedules": [],
                "voiceCommands": [],
                "eventTriggers": []
              }
            }
        """.trimIndent()

        val result = InstructionParser.parse(json)
        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertEquals("Test Agent", file.agent.name)
        assertEquals(1, file.agent.workflows.size)
        assertEquals("wf1", file.agent.workflows.first().id)
    }
}
