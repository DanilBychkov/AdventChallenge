package org.bothubclient.domain.repository

import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.domain.entity.Message

interface ChatRepository {
    suspend fun sendMessage(
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double = 0.7
    ): ChatResult

    suspend fun getHistory(): List<Message>

    suspend fun getSessionMessages(): List<Message>

    suspend fun resetSession() {}
}
