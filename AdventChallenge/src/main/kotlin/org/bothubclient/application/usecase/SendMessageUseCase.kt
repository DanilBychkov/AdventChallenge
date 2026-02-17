package org.bothubclient.application.usecase

import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.domain.repository.ChatRepository

class SendMessageUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(
        userMessage: String,
        model: String,
        systemPrompt: String
    ): ChatResult {
        return chatRepository.sendMessage(userMessage, model, systemPrompt)
    }
}
