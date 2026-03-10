package org.bothubclient.application.mcp

import org.bothubclient.domain.entity.McpServerConfig

/**
 * Heuristic Context7 relevance check for documentation and SDK-centric questions.
 */
fun isContext7Relevant(userMessage: String): Boolean {
    return context7RelevanceStrategy
        .isRelevant(
            server = CONTEXT7_DUMMY_SERVER,
            userMessage = userMessage,
            context = null
        )
        .relevant
}

private val context7RelevanceStrategy = Context7RelevanceStrategy()

private val CONTEXT7_DUMMY_SERVER = McpServerConfig(
    id = CONTEXT7_SERVER_TYPE,
    name = "Context7",
    type = CONTEXT7_SERVER_TYPE,
    description = ""
)
