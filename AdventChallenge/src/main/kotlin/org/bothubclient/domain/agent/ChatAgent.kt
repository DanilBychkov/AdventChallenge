package org.bothubclient.domain.agent

import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.SessionTokenStatistics
import org.bothubclient.infrastructure.api.ApiToolDefinition

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

    suspend fun sendWithTools(
        sessionId: String,
        contextMessages: List<Message>,
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double,
        tools: List<ApiToolDefinition>,
        toolExecutor: suspend (toolName: String, arguments: String) -> String,
        onToolCall: suspend (toolName: String, arguments: String, result: String, durationMs: Long) -> Unit
    ): ChatResult =
        sendWithContext(sessionId, contextMessages, userMessage, model, systemPrompt, temperature)

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
