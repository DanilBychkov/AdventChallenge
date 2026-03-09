package org.bothubclient.application.mcp

import org.bothubclient.infrastructure.logging.AppLogger

/**
 * Result of MCP enrichment: discovery (tools/resources the model "sees") and pre-fetched content.
 * The host injects both into the agent harness so the LLM knows what MCP can do and gets context.
 */
data class McpEnrichedContext(
    val discoverySummary: String,
    val content: String,
    /** Last MCP fetch failure reason when content is empty; for UI display. */
    val lastError: String? = null
) {
    val hasContent: Boolean get() = content.isNotBlank()
    val hasDiscovery: Boolean get() = discoverySummary.isNotBlank()
}

class McpContextOrchestrator(
    private val mcpRouter: McpRouter,
    private val mcpClient: McpClient
) {
    private companion object {
        const val TAG = "McpContextOrchestrator"
    }

    /**
     * Performs discovery (tools/list per server) and fetches context for the user message.
     * Context7 uses the API search (libs/search) via resolve-library-id to get library ID, then get-library-docs for context.
     */
    suspend fun fetchEnrichedContext(
        userMessage: String,
        sessionId: String? = null
    ): McpEnrichedContext {
        val selection =
            mcpRouter.selectForRequest(
                userMessage = userMessage,
                context = McpRequestContext(sessionId = sessionId)
            )

        val allSelected = selection.forcedServers + selection.optionalServers
        val discoveryResults = allSelected.mapNotNull { mcpClient.discover(it) }
        val discoverySummary = buildDiscoverySummary(discoveryResults)

        AppLogger.i(
            TAG,
            "MCP selection sessionId=$sessionId forced=${selection.forcedServers.map { it.id }} optional=${selection.optionalServers.map { it.id }} discoveryServers=${discoveryResults.size}"
        )

        val chunks = mutableListOf<String>()
        var lastFailureReason: String? = null

        selection.forcedServers.forEach { server ->
            when (val result = mcpClient.fetchContext(server, userMessage)) {
                is McpFetchResult.Success -> {
                    if (result.content.isNotBlank()) {
                        chunks += result.content.trim()
                    }
                    AppLogger.i(
                        TAG,
                        "MCP fetch server=${server.id} type=${server.type} mode=forced success=true"
                    )
                }

                is McpFetchResult.Failure -> {
                    lastFailureReason = result.reason
                    AppLogger.w(
                        TAG,
                        "MCP fetch server=${server.id} type=${server.type} mode=forced success=false reason=${result.reason}"
                    )
                }
            }
        }

        val isRelevant = isContext7Relevant(userMessage)
        val shouldUseOptional = selection.optionalServers.isNotEmpty() && isRelevant
        AppLogger.i(
            TAG,
            "MCP shouldUseOptional=$shouldUseOptional isContext7Relevant=$isRelevant optionalCount=${selection.optionalServers.size}"
        )
        if (!shouldUseOptional) {
            if (selection.optionalServers.isNotEmpty()) {
                AppLogger.i(
                    TAG,
                    "MCP optional skipped sessionId=$sessionId reason=context7_not_relevant"
                )
            }
            val contentStr = chunks.joinToString(separator = "\n")
            AppLogger.i(
                TAG,
                "MCP fetchEnrichedContext RETURN (no optional) contentLen=${contentStr.length} lastError=$lastFailureReason"
            )
            return McpEnrichedContext(
                discoverySummary = discoverySummary,
                content = contentStr,
                lastError = if (contentStr.isBlank() && lastFailureReason != null) lastFailureReason else null
            )
        }

        for (server in selection.optionalServers) {
            AppLogger.i(TAG, "MCP optional fetch START server=${server.id} type=${server.type}")
            when (val result = mcpClient.fetchContext(server, userMessage)) {
                is McpFetchResult.Success -> {
                    if (result.content.isNotBlank()) {
                        chunks += result.content.trim()
                    }
                    AppLogger.i(
                        TAG,
                        "MCP fetch server=${server.id} mode=optional success=true contentLen=${result.content.length}"
                    )
                    break
                }

                is McpFetchResult.Failure -> {
                    lastFailureReason = result.reason
                    AppLogger.w(
                        TAG,
                        "MCP fetch server=${server.id} mode=optional success=false reason=${result.reason}"
                    )
                }
            }
        }

        val contentStr = chunks.joinToString(separator = "\n")
        AppLogger.i(
            TAG,
            "MCP fetchEnrichedContext RETURN contentLen=${contentStr.length} lastError=$lastFailureReason"
        )
        return McpEnrichedContext(
            discoverySummary = discoverySummary,
            content = contentStr,
            lastError = if (contentStr.isBlank() && lastFailureReason != null) lastFailureReason else null
        )
    }

    private fun buildDiscoverySummary(results: List<McpDiscoveryResult>): String {
        if (results.isEmpty()) return ""
        return results.joinToString(separator = "\n\n") { r ->
            val parts = mutableListOf<String>()
            val header = "**${r.serverLabel}** (server: ${r.serverId})"
            val toolLines = r.tools.map { t ->
                val params = if (!t.inputSchemaSummary.isNullOrBlank()) " (params: ${t.inputSchemaSummary})" else ""
                val desc = t.description?.take(200)?.let { " — $it" } ?: ""
                "- ${t.name}$params$desc"
            }
            if (toolLines.isNotEmpty()) {
                parts += "Tools:\n" + toolLines.joinToString("\n")
            }
            if (r.resources.isNotEmpty()) {
                val resLines = r.resources.map { res ->
                    val desc = res.description?.take(120)?.let { " — $it" } ?: ""
                    "- ${res.uri}${res.name?.let { " ($it)" } ?: ""}$desc"
                }
                parts += "Resources:\n" + resLines.joinToString("\n")
            }
            if (r.prompts.isNotEmpty()) {
                val promptLines = r.prompts.map { p ->
                    val desc = p.description?.take(120)?.let { " — $it" } ?: ""
                    "- ${p.name}$desc"
                }
                parts += "Prompts:\n" + promptLines.joinToString("\n")
            }
            if (parts.isEmpty()) header else "$header\n" + parts.joinToString("\n\n")
        }.trim()
    }
}
