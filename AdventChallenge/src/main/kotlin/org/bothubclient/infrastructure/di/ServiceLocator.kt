package org.bothubclient.infrastructure.di

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.bothubclient.application.usecase.*
import org.bothubclient.domain.agent.ChatAgent
import org.bothubclient.domain.repository.ApiKeyProvider
import org.bothubclient.domain.repository.ChatHistoryStorage
import org.bothubclient.domain.repository.ChatRepository
import org.bothubclient.infrastructure.agent.AgentBackedChatRepository
import org.bothubclient.infrastructure.agent.BothubChatAgent
import org.bothubclient.infrastructure.api.BothubChatRepository
import org.bothubclient.infrastructure.config.EnvironmentApiKeyProvider
import org.bothubclient.infrastructure.persistence.FileChatHistoryStorage

object ServiceLocator {
    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                )
            }
        }
    }

    private val apiKeyProvider: ApiKeyProvider by lazy { EnvironmentApiKeyProvider() }

    private val chatHistoryStorage: ChatHistoryStorage by lazy { FileChatHistoryStorage() }

    private val statelessChatRepository: ChatRepository by lazy {
        BothubChatRepository(httpClient) { apiKeyProvider.getApiKey() }
    }

    private val chatAgent: ChatAgent by lazy {
        BothubChatAgent(client = httpClient, getApiKey = { apiKeyProvider.getApiKey() })
    }

    private val chatRepository: ChatRepository by lazy {
        AgentBackedChatRepository(agent = chatAgent, sessionId = "chat-ui")
    }

    val sendMessageUseCase: SendMessageUseCase by lazy { SendMessageUseCase(chatRepository) }

    val resetChatSessionUseCase: ResetChatSessionUseCase by lazy {
        ResetChatSessionUseCase(chatRepository)
    }

    val getChatHistoryUseCase: GetChatHistoryUseCase by lazy {
        GetChatHistoryUseCase(chatRepository)
    }

    val getSessionMessagesUseCase: GetSessionMessagesUseCase by lazy {
        GetSessionMessagesUseCase(chatRepository)
    }

    val getAvailableModelsUseCase: GetAvailableModelsUseCase by lazy { GetAvailableModelsUseCase() }

    val getSystemPromptsUseCase: GetSystemPromptsUseCase by lazy { GetSystemPromptsUseCase() }

    val validateApiKeyUseCase: ValidateApiKeyUseCase by lazy {
        ValidateApiKeyUseCase(apiKeyProvider)
    }

    val optimizePromptUseCase: OptimizePromptUseCase by lazy {
        OptimizePromptUseCase(statelessChatRepository)
    }

    val getTokenStatisticsUseCase: GetTokenStatisticsUseCase by lazy {
        GetTokenStatisticsUseCase(chatAgent) { "chat-ui" }
    }

    fun close() {
        httpClient.close()
    }
}
