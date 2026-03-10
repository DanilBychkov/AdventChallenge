package org.bothubclient.application.mcp

import org.bothubclient.domain.entity.McpServerConfig

const val BORED_API_SERVER_TYPE = "bored-api"

class BoredApiRelevanceStrategy : McpRelevanceStrategy {

    override fun isRelevant(
        server: McpServerConfig,
        userMessage: String,
        context: McpRequestContext?
    ): McpRelevanceResult {
        val relevant = isBoredApiRelevantMessage(userMessage)
        return McpRelevanceResult(
            relevant = relevant,
            reason = if (relevant) "bored_api_keyword_match" else "bored_api_no_keyword_match"
        )
    }
}

internal fun isBoredApiRelevantMessage(userMessage: String): Boolean {
    val normalizedMessage = userMessage.lowercase()
    return BORED_API_RELEVANCE_KEYWORDS.any { keyword -> normalizedMessage.contains(keyword) }
}

internal val BORED_API_RELEVANCE_KEYWORDS = listOf(
    // English: strong boredom/activity intent only (no generic "idea"/"suggest")
    "activity",
    "activities",
    "bored",
    "boredom",
    "what to do",
    "something to do",
    "random activity",
    // Russian: strong boredom/activity/leisure intent only (no generic "идея"/"предложи")
    "скучно",
    "мне скучно",
    "занятие",
    "занятия",
    "чем заняться",
    "что делать",
    "развлечение",
    "развлечения",
    "развлечений",
    "чем развлечься",
    "какое занятие",
    "подскажи чем заняться",
    "хочу чем-то заняться",
    "досуг"
)
