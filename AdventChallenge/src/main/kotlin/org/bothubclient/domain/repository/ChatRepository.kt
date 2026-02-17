package org.bothubclient.domain.repository

import org.bothubclient.domain.entity.ChatResult

interface ChatRepository {
    suspend fun sendMessage(
        userMessage: String,
        model: String,
        systemPrompt: String
    ): ChatResult
}
