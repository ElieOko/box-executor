package com.appbox.runtime.core.instruction

import kotlinx.serialization.json.Json

object InstructionParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun parse(content: String): Result<InstructionFile> = runCatching {
        json.decodeFromString<InstructionFile>(content.trim())
    }

    fun serialize(file: InstructionFile): String =
        json.encodeToString(InstructionFile.serializer(), file)
}
