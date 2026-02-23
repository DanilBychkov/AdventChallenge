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
import org.bothubclient.domain.entity.RequestMetrics
import org.bothubclient.domain.repository.ChatRepository
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

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

            var responseTimeMs = 0L
            val chatResponse: ApiChatResponse
            responseTimeMs = measureTimeMillis {
                val url = ApiConfig.BASE_URL.trim().trimEnd(',', ' ')
                val response: HttpResponse = client.post(url) {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $apiKey")
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    }
                    timeout {
                        requestTimeoutMillis = 600_000
                        socketTimeoutMillis = 600_000
                    }
                    setBody(request)
                }

                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    return ChatResult.Error(Exception("HTTP ошибка ${response.status}: $errorBody"))
                }

                chatResponse = response.body<ApiChatResponse>()
            }

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
                    val metrics = RequestMetrics(
                        promptTokens = chatResponse.usage?.prompt_tokens ?: 0,
                        completionTokens = chatResponse.usage?.completion_tokens ?: 0,
                        totalTokens = chatResponse.usage?.total_tokens ?: 0,
                        responseTimeMs = responseTimeMs
                    )
                    ChatResult.Success(Message.assistant(content), metrics)
                }
            }
        } catch (e: Exception) {
            ChatResult.Error(e)
        }
    }

    companion object {
        fun createDefault(getApiKey: () -> String): BothubChatRepository {
            val requestTimeoutMs = 600_000L
            val connectTimeoutMs = 30_000L
            val socketTimeoutMs = 600_000L

            val client = HttpClient(OkHttp) {
                engine {
                    config {
                        callTimeout(requestTimeoutMs, TimeUnit.MILLISECONDS)
                        connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                        readTimeout(socketTimeoutMs, TimeUnit.MILLISECONDS)
                        writeTimeout(socketTimeoutMs, TimeUnit.MILLISECONDS)
                    }
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = requestTimeoutMs
                    connectTimeoutMillis = connectTimeoutMs
                    socketTimeoutMillis = socketTimeoutMs
                }
                install(HttpRequestRetry) {
                    maxRetries = 2
                    retryOnExceptionIf { _, cause ->
                        cause is SocketTimeoutException ||
                                cause is HttpRequestTimeoutException
                    }
                    exponentialDelay()
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
