package org.bothubclient.infrastructure.mcp

import kotlinx.serialization.json.*
import org.bothubclient.infrastructure.logging.AppLogger

class Context7StdioFetchStrategy : StdioMcpFetchStrategy {
    private companion object {
        const val TAG = "Context7StdioFetchStrategy"
        const val RESOLVE_TOOL = "resolve-library-id"
        const val GET_DOCS_TOOL = "get-library-docs"
        const val QUERY_DOCS_TOOL = "query-docs"
    }

    override suspend fun resolvePreference(
        session: StdioMcpToolSession,
        tools: List<JsonObject>,
        query: String
    ): String? {
        val resolveToolName = tools
            .firstOrNull {
                it["name"]?.jsonPrimitive?.contentOrNull?.contains(RESOLVE_TOOL, ignoreCase = true) == true
            }
            ?.get("name")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: run {
                AppLogger.w(TAG, "[MCP] resolvePreference: no $RESOLVE_TOOL tool found")
                return null
            }

        val baseName = Context7QueryHelper.extractLibraryName(query)
        AppLogger.i(TAG, "[MCP] resolvePreference baseName='$baseName'")
        if (baseName.isBlank()) return null

        val namesToTry = Context7QueryHelper.libraryNameVariants(baseName)
        AppLogger.i(TAG, "[MCP] resolvePreference namesToTry=$namesToTry")

        for (libraryName in namesToTry) {
            val response = session.callTool(
                toolName = resolveToolName,
                arguments = buildJsonObject {
                    put("libraryName", libraryName)
                    put("query", query.take(500))
                }
            ) ?: continue

            val content = extractContent(response)
            val parsedId = Context7QueryHelper.parseLibraryIdFromResolveResponse(content)
            AppLogger.i(
                TAG,
                "[MCP] resolvePreference attempt libraryName='$libraryName' contentLen=${content.length} parsedId=$parsedId"
            )
            if (parsedId != null) {
                AppLogger.i(TAG, "[MCP] resolvePreference SUCCESS resolvedId='$parsedId'")
                return parsedId
            }
        }

        AppLogger.w(TAG, "[MCP] resolvePreference FAIL no ID from any variant")
        return null
    }

    override fun prioritizeTools(tools: List<JsonObject>): List<JsonObject> {
        val weights = mapOf(
            GET_DOCS_TOOL to 0,
            QUERY_DOCS_TOOL to 0,
            "search" to 1,
            "query" to 2,
            "lookup" to 3,
            RESOLVE_TOOL to 4
        )

        return tools.sortedBy { tool ->
            val name = tool["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            weights.entries.firstOrNull { name.contains(it.key, ignoreCase = true) }?.value ?: 10
        }
    }

    override fun buildArgumentCandidates(
        tool: JsonObject,
        query: String,
        resolvedPreference: String?
    ): List<JsonObject> {
        val toolName = tool["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val schema = tool["inputSchema"]?.jsonObject
        val properties = schema?.get("properties")?.jsonObject.orEmpty()
        val required = schema?.get("required")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val extractedName = Context7QueryHelper.extractLibraryName(query)

        val inferred = buildJsonObject {
            val keys = if (required.isNotEmpty()) required else properties.keys.toList()
            keys.forEach { key -> put(key, query) }
            if (keys.isEmpty()) {
                put("query", query)
            }
        }

        val generic = buildJsonObject { put("query", query) }
        val context7WithQuery = buildJsonObject {
            put("libraryName", query)
            put("context7CompatibleLibraryID", query)
            put("topic", query)
            put("tokens", 6000)
        }

        return buildList {
            val isGetLibraryDocs = toolName.contains(GET_DOCS_TOOL, ignoreCase = true)
            val isQueryDocs = toolName.contains(QUERY_DOCS_TOOL, ignoreCase = true)

            if (isQueryDocs && resolvedPreference != null) {
                add(
                    buildJsonObject {
                        put("libraryId", resolvedPreference)
                        put("topic", extractedName.take(200))
                        put("tokens", 6000)
                    }
                )
                add(
                    buildJsonObject {
                        put("libraryId", resolvedPreference)
                        put("query", extractedName.take(200))
                        put("tokens", 6000)
                    }
                )
            }

            if (isGetLibraryDocs) {
                if (resolvedPreference != null) {
                    add(
                        buildJsonObject {
                            put("context7CompatibleLibraryID", resolvedPreference)
                            put("topic", extractedName.take(200))
                            put("tokens", 6000)
                        }
                    )
                }
                add(context7WithQuery)
            }

            if (toolName.contains(RESOLVE_TOOL, ignoreCase = true)) {
                add(
                    buildJsonObject {
                        put("libraryName", extractedName)
                        put("query", query.take(500))
                    }
                )
            }

            add(inferred)
            add(generic)
        }.distinctBy { it.toString() }
    }

    private fun extractContent(response: JsonObject): String {
        val result = response["result"] ?: return ""
        return extractTextFromJson(result).trim()
    }

    private fun extractTextFromJson(element: JsonElement): String {
        return when (element) {
            is JsonPrimitive -> element.contentOrNull.orEmpty()
            is JsonArray -> element.joinToString("\n") { extractTextFromJson(it) }.trim()
            is JsonObject -> {
                val preferredKeys = listOf("text", "content", "message", "value", "output")
                for (key in preferredKeys) {
                    val value = element[key] ?: continue
                    val text = extractTextFromJson(value)
                    if (text.isNotBlank()) {
                        return text
                    }
                }
                element.values.joinToString("\n") { extractTextFromJson(it) }.trim()
            }
        }
    }
}
