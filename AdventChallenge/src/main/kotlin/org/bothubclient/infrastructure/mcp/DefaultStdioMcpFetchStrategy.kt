package org.bothubclient.infrastructure.mcp

import kotlinx.serialization.json.*

class DefaultStdioMcpFetchStrategy : StdioMcpFetchStrategy {
    override suspend fun resolvePreference(
        session: StdioMcpToolSession,
        tools: List<JsonObject>,
        query: String
    ): String? = null

    override fun prioritizeTools(tools: List<JsonObject>): List<JsonObject> = tools

    override fun buildArgumentCandidates(
        tool: JsonObject,
        query: String,
        resolvedPreference: String?
    ): List<JsonObject> {
        val schema = tool["inputSchema"]?.jsonObject
        val properties = schema?.get("properties")?.jsonObject.orEmpty()
        val required = schema?.get("required")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

        val inferred = buildJsonObject {
            val keys = if (required.isNotEmpty()) required else properties.keys.toList()
            keys.forEach { key -> put(key, query) }
            if (keys.isEmpty()) {
                put("query", query)
            }
        }

        val generic = buildJsonObject { put("query", query) }
        return listOf(inferred, generic).distinctBy { it.toString() }
    }
}
