package org.bothubclient.application.mcp

import org.bothubclient.domain.entity.McpServerConfig

class FallbackRelevanceStrategy(
    private val defaultRelevant: Boolean = false,
    private val reason: String? = "fallback"
) : McpRelevanceStrategy {
    override fun isRelevant(
        server: McpServerConfig,
        userMessage: String,
        context: McpRequestContext?
    ): McpRelevanceResult {
        return McpRelevanceResult(
            relevant = defaultRelevant,
            reason = reason
        )
    }
}

