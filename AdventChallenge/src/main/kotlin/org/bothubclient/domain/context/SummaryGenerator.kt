package org.bothubclient.domain.context

import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.SummaryBlock

sealed class SummaryResult {
    data class Success(val block: SummaryBlock) : SummaryResult()
    data class Error(val exception: Exception) : SummaryResult()
}

interface SummaryGenerator {
    suspend fun generateSummary(
        messages: List<Message>,
        maxTokens: Int
    ): SummaryResult

    fun getSummaryPrompt(messages: List<Message>, maxTokens: Int): String
}
