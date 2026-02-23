package org.bothubclient.application.usecase

import org.bothubclient.domain.repository.ChatRepository

class ResetChatSessionUseCase(
    private val chatRepository: ChatRepository
) {
    operator fun invoke() {
        chatRepository.resetSession()
    }
}
