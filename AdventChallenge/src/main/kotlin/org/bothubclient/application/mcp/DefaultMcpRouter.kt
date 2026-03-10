package org.bothubclient.application.mcp

import org.bothubclient.domain.repository.McpRegistry

/**
 * Default MCP router that splits enabled servers into forced and optional groups,
 * filtering optional servers by per-server relevance strategies.
 */
class DefaultMcpRouter(
    private val registry: McpRegistry,
    private val relevanceRegistry: McpRelevanceStrategyRegistry
) : McpRouter {

    override suspend fun selectForRequest(
        userMessage: String,
        context: McpRequestContext?
    ): McpSelectionResult {
        val forcedServers = registry.getForced()

        val enabledOptionalServers = registry.getEnabled().filter { !it.forceUsage }

        val optionalPassed = mutableListOf<String>()
        val optionalFiltered = mutableListOf<String>()
        val relevantOptionalServers = enabledOptionalServers.filter { server ->
            val strategy = relevanceRegistry.getStrategy(server)
            val result = strategy.isRelevant(server, userMessage, context)
            if (result.relevant) {
                optionalPassed.add(server.id)
                true
            } else {
                val filterReason = if (result.reason != null) "${server.id}: ${result.reason}" else server.id
                optionalFiltered.add(filterReason)
                false
            }
        }

        return McpSelectionResult(
            forcedServers = forcedServers,
            optionalServers = relevantOptionalServers,
            metadata = mapOf(
                "strategy" to "enabled_split_forced_optional_relevance",
                "optionalPassed" to optionalPassed.joinToString(","),
                "optionalFiltered" to optionalFiltered.joinToString(";")
            )
        )
    }
}
