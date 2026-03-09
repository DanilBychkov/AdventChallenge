package org.bothubclient.application.mcp

import org.bothubclient.domain.entity.McpServerConfig

/**
 * Selects MCP servers for the current request without executing MCP calls.
 */
interface McpRouter {
    suspend fun selectForRequest(
        userMessage: String,
        context: McpRequestContext? = null
    ): McpSelectionResult
}

/**
 * Optional context for selection and logging metadata.
 */
data class McpRequestContext(
    val sessionId: String? = null,
    val conversationId: String? = null,
    val tags: Set<String> = emptySet()
)

/**
 * Routing decision: forced servers are attempted first, optional may be used by the agent.
 */
data class McpSelectionResult(
    val forcedServers: List<McpServerConfig>,
    val optionalServers: List<McpServerConfig>,
    val metadata: Map<String, String> = emptyMap()
)
