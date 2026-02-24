package org.bothubclient.domain.agent

import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.domain.entity.Message

interface ChatAgent {
    suspend fun send(
        sessionId: String,
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double
    ): ChatResult

    suspend fun getHistory(sessionId: String): List<Message>

    suspend fun getSessionMessages(sessionId: String): List<Message>

    suspend fun reset(sessionId: String)
}
