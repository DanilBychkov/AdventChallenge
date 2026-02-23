package org.bothubclient.domain.agent

import org.bothubclient.domain.entity.ChatResult

interface ChatAgent {
    suspend fun send(
        sessionId: String,
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double
    ): ChatResult

    fun reset(sessionId: String)
}
