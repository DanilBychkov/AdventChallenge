package org.bothubclient.infrastructure.di

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.bothubclient.application.usecase.*
import org.bothubclient.domain.repository.ApiKeyProvider
import org.bothubclient.domain.repository.ChatRepository
import org.bothubclient.infrastructure.api.BothubChatRepository
import org.bothubclient.infrastructure.config.EnvironmentApiKeyProvider

object ServiceLocator {
    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    private val apiKeyProvider: ApiKeyProvider by lazy {
        EnvironmentApiKeyProvider()
    }

    private val chatRepository: ChatRepository by lazy {
        BothubChatRepository(httpClient) { apiKeyProvider.getApiKey() }
    }

    val sendMessageUseCase: SendMessageUseCase by lazy {
        SendMessageUseCase(chatRepository)
    }

    val getAvailableModelsUseCase: GetAvailableModelsUseCase by lazy {
        GetAvailableModelsUseCase()
    }

    val getSystemPromptsUseCase: GetSystemPromptsUseCase by lazy {
        GetSystemPromptsUseCase()
    }

    val validateApiKeyUseCase: ValidateApiKeyUseCase by lazy {
        ValidateApiKeyUseCase(apiKeyProvider)
    }

    val optimizePromptUseCase: OptimizePromptUseCase by lazy {
        OptimizePromptUseCase(chatRepository)
    }

    fun close() {
        httpClient.close()
    }
}
