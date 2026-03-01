package org.bothubclient.domain.agent

import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.SessionTokenStatistics

interface ChatAgent {
    suspend fun send(
        sessionId: String,
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double
    ): ChatResult

    suspend fun sendWithContext(
        sessionId: String,
        contextMessages: List<Message>,
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double
    ): ChatResult =
        send(
            sessionId = sessionId,
            userMessage = userMessage,
            model = model,
            systemPrompt = systemPrompt,
            temperature = temperature
        )

    suspend fun getHistory(sessionId: String): List<Message>

    suspend fun getSessionMessages(sessionId: String): List<Message>

    suspend fun reset(sessionId: String)

    fun getSessionTokenStatistics(sessionId: String, model: String): SessionTokenStatistics

    fun getTotalHistoryTokens(sessionId: String): Int

    fun isApproachingContextLimit(
        sessionId: String,
        model: String,
        threshold: Float = 0.8f
    ): Boolean

    fun truncateHistory(sessionId: String, keepLast: Int = 10)

    fun removeOldestMessages(sessionId: String, count: Int): List<Message>
}
