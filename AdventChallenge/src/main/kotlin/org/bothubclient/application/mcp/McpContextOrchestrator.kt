package org.bothubclient.application.mcp

import org.bothubclient.infrastructure.logging.AppLogger

class McpContextOrchestrator(
    private val mcpRouter: McpRouter,
    private val mcpClient: McpClient
) {
    private companion object {
        const val TAG = "McpContextOrchestrator"
    }

    suspend fun fetchEnrichedContext(
        userMessage: String,
        sessionId: String? = null
    ): String {
        val selection =
            mcpRouter.selectForRequest(
                userMessage = userMessage,
                context = McpRequestContext(sessionId = sessionId)
            )

        AppLogger.i(
            TAG,
            "MCP selection sessionId=$sessionId forced=${selection.forcedServers.map { it.id }} optional=${selection.optionalServers.map { it.id }}"
        )

        val chunks = mutableListOf<String>()

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
                    AppLogger.w(
                        TAG,
                        "MCP fetch server=${server.id} type=${server.type} mode=forced success=false reason=${result.reason}"
                    )
                }
            }
        }

        val shouldUseOptional =
            selection.optionalServers.isNotEmpty() && isContext7Relevant(userMessage)
        if (!shouldUseOptional) {
            if (selection.optionalServers.isNotEmpty()) {
                AppLogger.i(
                    TAG,
                    "MCP optional skipped sessionId=$sessionId reason=context7_not_relevant"
                )
            }
            return chunks.joinToString(separator = "\n")
        }

        for (server in selection.optionalServers) {
            when (val result = mcpClient.fetchContext(server, userMessage)) {
                is McpFetchResult.Success -> {
                    if (result.content.isNotBlank()) {
                        chunks += result.content.trim()
                    }
                    AppLogger.i(
                        TAG,
                        "MCP fetch server=${server.id} type=${server.type} mode=optional success=true"
                    )
                    break
                }

                is McpFetchResult.Failure -> {
                    AppLogger.w(
                        TAG,
                        "MCP fetch server=${server.id} type=${server.type} mode=optional success=false reason=${result.reason}"
                    )
                }
            }
        }

        return chunks.joinToString(separator = "\n")
    }
}
