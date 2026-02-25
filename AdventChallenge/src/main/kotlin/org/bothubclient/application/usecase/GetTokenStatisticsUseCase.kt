package org.bothubclient.application.usecase

import org.bothubclient.domain.agent.ChatAgent
import org.bothubclient.domain.entity.SessionTokenStatistics

class GetTokenStatisticsUseCase(
    private val chatAgent: ChatAgent,
    private val getSessionId: () -> String
) {
    operator fun invoke(model: String): SessionTokenStatistics {
        return chatAgent.getSessionTokenStatistics(getSessionId(), model)
    }
}
