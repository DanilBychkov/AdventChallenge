package org.bothubclient.infrastructure.agent

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.bothubclient.config.ApiConfig
import org.bothubclient.domain.agent.ChatAgent
import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.MessageRole
import org.bothubclient.domain.entity.RequestMetrics
import org.bothubclient.domain.repository.ChatHistoryStorage
import org.bothubclient.infrastructure.api.ApiChatMessage
import org.bothubclient.infrastructure.api.ApiChatRequest
import org.bothubclient.infrastructure.api.ApiChatResponse
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

class BothubChatAgent(
    private val client: HttpClient,
    private val getApiKey: () -> String,
    private val maxHistoryMessages: Int = 20,
    private val storage: ChatHistoryStorage? = null
) : ChatAgent {

    private val historyBySessionId = ConcurrentHashMap<String, MutableList<Message>>()
    private val loadedSessions = ConcurrentHashMap<String, Unit>().keySet(Unit)
    private val persistedCountBySession = ConcurrentHashMap<String, Int>()

    override suspend fun getHistory(sessionId: String): List<Message> {
        ensureHistoryLoaded(sessionId)
        return historyBySessionId[sessionId]?.toList().orEmpty()
    }

    override suspend fun getSessionMessages(sessionId: String): List<Message> {
        ensureHistoryLoaded(sessionId)
        val history = historyBySessionId[sessionId] ?: return emptyList()
        val persistedCount = persistedCountBySession[sessionId] ?: 0
        return if (persistedCount < history.size) {
            history.subList(persistedCount, history.size).toList()
        } else {
            emptyList()
        }
    }

    override suspend fun reset(sessionId: String) {
        historyBySessionId.remove(sessionId)
        persistedCountBySession.remove(sessionId)
        loadedSessions.remove(sessionId)
        storage?.deleteHistory(sessionId)
    }

    private suspend fun ensureHistoryLoaded(sessionId: String) {
        if (sessionId !in loadedSessions && storage != null) {
            println("[BothubChatAgent] Loading history for session: $sessionId")
            val saved = storage.loadHistory(sessionId)
            println("[BothubChatAgent] Loaded ${saved.size} messages from storage")
            synchronized(this) {
                if (sessionId !in loadedSessions) {
                    if (saved.isNotEmpty()) {
                        historyBySessionId[sessionId] = saved.toMutableList()
                        persistedCountBySession[sessionId] = saved.size
                        println(
                            "[BothubChatAgent] History set for session, persistedCount = ${saved.size}"
                        )
                    } else {
                        historyBySessionId[sessionId] = mutableListOf()
                        persistedCountBySession[sessionId] = 0
                        println("[BothubChatAgent] No saved history, initialized empty list")
                    }
                    loadedSessions.add(sessionId)
                }
            }
        } else if (sessionId in loadedSessions) {
            println(
                "[BothubChatAgent] Session already loaded: $sessionId, history size = ${historyBySessionId[sessionId]?.size ?: 0}"
            )
        }
    }

    override suspend fun send(
        sessionId: String,
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double
    ): ChatResult {
        ensureHistoryLoaded(sessionId)
        return try {
            val apiKey = getApiKey()
            val trimmedUrl = ApiConfig.BASE_URL.trim().trimEnd(',', ' ')

            val apiMessages = buildList {
                add(ApiChatMessage(role = "system", content = systemPrompt))

                val past = historyBySessionId[sessionId]?.toList().orEmpty()
                println("[BothubChatAgent] Building API messages, past history size = ${past.size}")
                past.forEach { message ->
                    val role =
                        when (message.role) {
                            MessageRole.USER -> "user"
                            MessageRole.ASSISTANT -> "assistant"
                            else -> null
                        }
                    if (role != null) {
                        println(
                            "[BothubChatAgent] Adding message: role=$role, content=${message.content.take(50)}..."
                        )
                        add(ApiChatMessage(role = role, content = message.content))
                    }
                }

                add(ApiChatMessage(role = "user", content = userMessage))
            }
            println("[BothubChatAgent] Total API messages count = ${apiMessages.size}")

            val request =
                ApiChatRequest(
                    model = model,
                    messages = apiMessages,
                    max_tokens = ApiConfig.DEFAULT_MAX_TOKENS,
                    temperature = temperature
                )

            var responseTimeMs = 0L
            val chatResponse: ApiChatResponse
            responseTimeMs = measureTimeMillis {
                val response: HttpResponse =
                    client.post(trimmedUrl) {
                        headers {
                            append(HttpHeaders.Authorization, "Bearer $apiKey")
                            append(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString()
                            )
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

                chatResponse = response.body()
            }

            when {
                chatResponse.error != null -> {
                    ChatResult.Error(Exception("API ошибка: ${chatResponse.error.message}"))
                }
                chatResponse.choices.isNullOrEmpty() -> {
                    ChatResult.Error(Exception("Не удалось получить ответ от модели"))
                }
                else -> {
                    val content =
                        chatResponse.choices.first().message?.content
                            ?: "Не удалось получить ответ от модели"

                    historyBySessionId.compute(sessionId) { _, current ->
                        val updated =
                            (current ?: mutableListOf()).apply {
                                add(Message.user(userMessage))
                                add(Message.assistant(content))
                                while (size > maxHistoryMessages) {
                                    removeAt(0)
                                }
                            }
                        updated
                    }

                    val currentSize = historyBySessionId[sessionId]?.size ?: 0
                    val currentPersisted = persistedCountBySession[sessionId] ?: 0
                    if (currentPersisted > currentSize) {
                        persistedCountBySession[sessionId] = currentSize
                    }

                    storage?.saveHistory(
                        sessionId,
                        historyBySessionId[sessionId]?.toList().orEmpty()
                    )
                    println(
                        "[BothubChatAgent] Saved history, total size = ${historyBySessionId[sessionId]?.size ?: 0}"
                    )

                    val metrics =
                        RequestMetrics(
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
}
