package org.bothubclient.application.mcp

private val CONTEXT7_RELEVANCE_KEYWORDS = listOf(
    "documentation",
    "docs",
    "api",
    "how to use",
    "example",
    "examples",
    "migration",
    "migrate",
    "upgrade",
    "library",
    "framework",
    "sdk",
    "package",
    "npm",
    "pip",
    "latest version"
)

/**
 * Heuristic Context7 relevance check for documentation and SDK-centric questions.
 */
fun isContext7Relevant(userMessage: String): Boolean {
    val normalizedMessage = userMessage.lowercase()
    return CONTEXT7_RELEVANCE_KEYWORDS.any { keyword -> normalizedMessage.contains(keyword) }
}
