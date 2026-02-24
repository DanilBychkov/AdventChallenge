package org.bothubclient.infrastructure.agent

import org.bothubclient.domain.agent.ChatAgent
import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.repository.ChatRepository

class AgentBackedChatRepository(
    private val agent: ChatAgent,
    private val sessionId: String
) : ChatRepository {
    override suspend fun sendMessage(
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double
    ): ChatResult {
        return agent.send(
            sessionId = sessionId,
            userMessage = userMessage,
            model = model,
            systemPrompt = systemPrompt,
            temperature = temperature
        )
    }

    override suspend fun getHistory(): List<Message> {
        return agent.getHistory(sessionId)
    }

    override suspend fun getSessionMessages(): List<Message> {
        return agent.getSessionMessages(sessionId)
    }

    override suspend fun resetSession() {
        agent.reset(sessionId)
    }
}
