package org.bothubclient.infrastructure.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.bothubclient.config.ApiConfig
import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.repository.ChatRepository
import java.util.concurrent.TimeUnit

class BothubChatRepository(
    private val client: HttpClient,
    private val getApiKey: () -> String
) : ChatRepository {

    override suspend fun sendMessage(
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double
    ): ChatResult {
        return try {
            val apiKey = getApiKey()
            val request = ApiChatRequest(
                model = model,
                messages = listOf(
                    ApiChatMessage(role = "system", content = systemPrompt),
                    ApiChatMessage(role = "user", content = userMessage)
                ),
                max_tokens = ApiConfig.DEFAULT_MAX_TOKENS,
                temperature = temperature
            )

            val response: HttpResponse = client.post(ApiConfig.BASE_URL) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                return ChatResult.Error(Exception("HTTP ошибка ${response.status}: $errorBody"))
            }

            val chatResponse = response.body<ApiChatResponse>()

            when {
                chatResponse.error != null -> {
                    ChatResult.Error(Exception("API ошибка: ${chatResponse.error.message}"))
                }

                chatResponse.choices.isNullOrEmpty() -> {
                    ChatResult.Error(Exception("Не удалось получить ответ от модели"))
                }

                else -> {
                    val content = chatResponse.choices.first().message?.content
                        ?: "Не удалось получить ответ от модели"
                    ChatResult.Success(Message.assistant(content))
                }
            }
        } catch (e: Exception) {
            ChatResult.Error(e)
        }
    }

    companion object {
        fun createDefault(getApiKey: () -> String): BothubChatRepository {
            val client = HttpClient(OkHttp) {
                engine {
                    config {
                        callTimeout(120, TimeUnit.SECONDS)
                        connectTimeout(30, TimeUnit.SECONDS)
                        readTimeout(120, TimeUnit.SECONDS)
                        writeTimeout(120, TimeUnit.SECONDS)
                    }
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 120_000
                    connectTimeoutMillis = 30_000
                    socketTimeoutMillis = 120_000
                }
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    })
                }
            }
            return BothubChatRepository(client, getApiKey)
        }
    }
}
