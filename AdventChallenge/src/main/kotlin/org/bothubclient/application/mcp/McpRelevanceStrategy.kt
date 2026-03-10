package org.bothubclient.application.mcp

import org.bothubclient.domain.entity.McpServerConfig

data class McpRelevanceResult(
    val relevant: Boolean,
    val reason: String? = null
)

interface McpRelevanceStrategy {
    fun isRelevant(
        server: McpServerConfig,
        userMessage: String,
        context: McpRequestContext? = null
    ): McpRelevanceResult
}

