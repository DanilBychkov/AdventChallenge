package org.bothubclient.application.usecase

import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.repository.ChatRepository

class GetSessionMessagesUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(): List<Message> {
        return chatRepository.getSessionMessages()
    }
}
