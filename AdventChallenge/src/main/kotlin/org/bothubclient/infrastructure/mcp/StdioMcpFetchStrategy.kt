package org.bothubclient.infrastructure.mcp

import kotlinx.serialization.json.JsonObject

interface StdioMcpToolSession {
    suspend fun callTool(toolName: String, arguments: JsonObject): JsonObject?
}

interface StdioMcpFetchStrategy {
    suspend fun resolvePreference(
        session: StdioMcpToolSession,
        tools: List<JsonObject>,
        query: String
    ): String?

    fun prioritizeTools(tools: List<JsonObject>): List<JsonObject>

    fun buildArgumentCandidates(
        tool: JsonObject,
        query: String,
        resolvedPreference: String?
    ): List<JsonObject>
}
