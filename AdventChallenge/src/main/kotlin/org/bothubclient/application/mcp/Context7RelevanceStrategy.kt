package org.bothubclient.application.mcp

import org.bothubclient.domain.entity.McpServerConfig

const val CONTEXT7_SERVER_TYPE = "context7"

class Context7RelevanceStrategy : McpRelevanceStrategy {

    override fun isRelevant(
        server: McpServerConfig,
        userMessage: String,
        context: McpRequestContext?
    ): McpRelevanceResult {
        val relevant = isContext7RelevantMessage(userMessage)
        return McpRelevanceResult(
            relevant = relevant,
            reason = if (relevant) "context7_keyword_match" else "context7_no_keyword_match"
        )
    }
}

internal fun isContext7RelevantMessage(userMessage: String): Boolean {
    val normalizedMessage = userMessage.lowercase()
    return CONTEXT7_RELEVANCE_KEYWORDS.any { keyword -> normalizedMessage.contains(keyword) }
}

internal val CONTEXT7_RELEVANCE_KEYWORDS = listOf(
    "documentation",
    "документация",
    "документацию",
    "docs",
    "api",
    "how to use",
    "example",
    "examples",
    "migration",
    "migrate",
    "upgrade",
    "library",
    "coroutines",
    "framework",
    "sdk",
    "package",
    "npm",
    "pip",
    "latest version"
)

