package org.bothubclient.application.mcp

import org.bothubclient.domain.repository.McpRegistry

/**
 * Default MCP router that splits enabled servers into forced and optional groups.
 */
class DefaultMcpRouter(
    private val registry: McpRegistry
) : McpRouter {

    override suspend fun selectForRequest(
        userMessage: String,
        context: McpRequestContext?
    ): McpSelectionResult {
        val forcedServers = registry.getForced()
        val optionalServers = registry.getEnabled().filter { !it.forceUsage }

        return McpSelectionResult(
            forcedServers = forcedServers,
            optionalServers = optionalServers,
            metadata = mapOf(
                "strategy" to "enabled_split_forced_optional",
                "context7Relevant" to isContext7Relevant(userMessage).toString()
            )
        )
    }
}
