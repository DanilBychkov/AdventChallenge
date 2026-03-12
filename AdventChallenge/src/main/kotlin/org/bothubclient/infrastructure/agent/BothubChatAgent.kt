package org.bothubclient.infrastructure.agent

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.bothubclient.config.ApiConfig
import org.bothubclient.config.ModelContextLimits
import org.bothubclient.config.ModelPricing
import org.bothubclient.domain.agent.ChatAgent
import org.bothubclient.domain.entity.*
import org.bothubclient.infrastructure.api.*
import org.bothubclient.infrastructure.logging.AppLogger
import org.bothubclient.infrastructure.logging.FileLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

private class TokenAccumulator {
    private val _totalPromptTokens = AtomicInteger(0)
    private val _totalCompletionTokens = AtomicInteger(0)
    private val _totalTokens = AtomicInteger(0)
    private val _messageCount = AtomicInteger(0)
    private val _lastPromptTokens = AtomicInteger(0)
    private val _lastCompletionTokens = AtomicInteger(0)

    val totalPromptTokens: Int
        get() = _totalPromptTokens.get()
    val totalCompletionTokens: Int
        get() = _totalCompletionTokens.get()
    val totalTokens: Int
        get() = _totalTokens.get()
    val messageCount: Int
        get() = _messageCount.get()
    val lastPromptTokens: Int
        get() = _lastPromptTokens.get()
    val lastCompletionTokens: Int
        get() = _lastCompletionTokens.get()

    fun update(promptTokens: Int, completionTokens: Int, totalTokens: Int) {
        _totalPromptTokens.addAndGet(promptTokens)
        _totalCompletionTokens.addAndGet(completionTokens)
        _totalTokens.addAndGet(totalTokens)
        _messageCount.incrementAndGet()
        _lastPromptTokens.set(promptTokens)
        _lastCompletionTokens.set(completionTokens)
    }

    fun reduceProportionally(factor: Float) {
        _totalPromptTokens.set((_totalPromptTokens.get() * factor).toInt())
        _totalCompletionTokens.set((_totalCompletionTokens.get() * factor).toInt())
        _totalTokens.set((_totalTokens.get() * factor).toInt())
        _messageCount.set((_messageCount.get() * factor).toInt())
    }

    fun snapshot(): TokenSnapshot =
        TokenSnapshot(
            totalPromptTokens = _totalPromptTokens.get(),
            totalCompletionTokens = _totalCompletionTokens.get(),
            totalTokens = _totalTokens.get(),
            messageCount = _messageCount.get(),
            lastPromptTokens = _lastPromptTokens.get(),
            lastCompletionTokens = _lastCompletionTokens.get()
        )
}

private data class TokenSnapshot(
    val totalPromptTokens: Int,
    val totalCompletionTokens: Int,
    val totalTokens: Int,
    val messageCount: Int,
    val lastPromptTokens: Int,
    val lastCompletionTokens: Int
)

class BothubChatAgent(
    private val client: HttpClient,
    private val getApiKey: () -> String,
    private val maxHistoryMessages: Int = 20
) : ChatAgent {

    private val historyBySessionId = ConcurrentHashMap<String, MutableList<Message>>()
    private val sessionTokenAccumulator = ConcurrentHashMap<String, TokenAccumulator>()

    companion object {
        private const val TAG = "BothubChatAgent"
        private const val CONTEXT_LENGTH_EXCEEDED = "context_length_exceeded"

        private val TOOL_CALL_REGEX = Regex(
            """\[TOOL_CALL]\s*\n?\s*(\{.*?})\s*\n?\s*\[/TOOL_CALL]""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        fun parseTextToolCall(content: String): Pair<String, String>? {
            val match = TOOL_CALL_REGEX.find(content) ?: return null
            val jsonStr = match.groupValues[1].trim()
            return try {
                val parsed = kotlinx.serialization.json.Json.parseToJsonElement(jsonStr)
                val obj = parsed as? kotlinx.serialization.json.JsonObject ?: return null
                val nameElement = obj["name"] as? kotlinx.serialization.json.JsonPrimitive ?: return null
                val name = nameElement.content
                if (name.isBlank()) return null
                val args = obj["arguments"]?.toString() ?: "{}"
                Pair(name, args)
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun reset(sessionId: String) {
        AppLogger.i(TAG, "Resetting session: $sessionId")
        historyBySessionId.remove(sessionId)
        sessionTokenAccumulator.remove(sessionId)
    }

    override suspend fun getHistory(sessionId: String): List<Message> {
        return historyBySessionId[sessionId]?.toList().orEmpty()
    }

    override suspend fun getSessionMessages(sessionId: String): List<Message> {
        return historyBySessionId[sessionId]?.toList().orEmpty()
    }

    override fun getSessionTokenStatistics(
        sessionId: String,
        model: String
    ): SessionTokenStatistics {
        val accumulator = sessionTokenAccumulator[sessionId] ?: return SessionTokenStatistics.EMPTY
        val snapshot = accumulator.snapshot()

        val contextLimit = ModelContextLimits.getContextLimit(model)
        val contextUsagePercent =
            ModelContextLimits.getContextUsagePercent(snapshot.totalTokens, model)
        val estimatedCost =
            ModelPricing.calculateCostRub(
                model,
                snapshot.totalPromptTokens,
                snapshot.totalCompletionTokens
            )

        return SessionTokenStatistics(
            sessionId = sessionId,
            totalPromptTokens = snapshot.totalPromptTokens,
            totalCompletionTokens = snapshot.totalCompletionTokens,
            totalTokens = snapshot.totalTokens,
            messageCount = snapshot.messageCount,
            estimatedCostRub = estimatedCost,
            lastRequestTokens = snapshot.lastPromptTokens,
            lastResponseTokens = snapshot.lastCompletionTokens,
            contextLimit = contextLimit,
            contextUsagePercent = contextUsagePercent
        )
    }

    override fun getTotalHistoryTokens(sessionId: String): Int {
        return sessionTokenAccumulator[sessionId]?.totalTokens ?: 0
    }

    override fun isApproachingContextLimit(
        sessionId: String,
        model: String,
        threshold: Float
    ): Boolean {
        val totalTokens = getTotalHistoryTokens(sessionId)
        val isApproaching = ModelContextLimits.isApproachingLimit(totalTokens, model, threshold)

        if (isApproaching) {
            val usagePercent = ModelContextLimits.getContextUsagePercent(totalTokens, model)
            val remaining = ModelContextLimits.getRemainingTokens(totalTokens, model)

            if (usagePercent > 95f) {
                AppLogger.contextCritical(TAG, sessionId, usagePercent)
            } else {
                AppLogger.contextWarning(TAG, sessionId, usagePercent, remaining)
            }
        }

        return isApproaching
    }

    override fun truncateHistory(sessionId: String, keepLast: Int) {
        val history = historyBySessionId[sessionId] ?: return
        val originalSize = history.size

        if (originalSize > keepLast) {
            val toRemove = originalSize - keepLast
            repeat(toRemove) {
                if (history.isNotEmpty()) {
                    history.removeAt(0)
                }
            }

            val factor = keepLast.toFloat() / originalSize
            sessionTokenAccumulator[sessionId]?.reduceProportionally(factor)

            AppLogger.i(
                TAG,
                "Truncated history for session $sessionId: removed $toRemove messages, keeping $keepLast, token stats reduced by ${(1 - factor) * 100}%"
            )
        }
    }

    override fun removeOldestMessages(sessionId: String, count: Int): List<Message> {
        val history = historyBySessionId[sessionId] ?: return emptyList()
        val removed = mutableListOf<Message>()

        synchronized(history) {
            repeat(count) {
                if (history.isNotEmpty()) {
                    removed.add(history.removeAt(0))
                }
            }
        }

        if (removed.isNotEmpty()) {
            val originalSize = history.size + removed.size
            val factor = if (originalSize > 0) history.size.toFloat() / originalSize else 0f
            sessionTokenAccumulator[sessionId]?.reduceProportionally(factor)

            AppLogger.i(
                TAG,
                "Removed ${removed.size} oldest messages from session $sessionId, token stats reduced by ${(1 - factor) * 100}%"
            )
        }

        return removed
    }

    override suspend fun send(
        sessionId: String,
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double
    ): ChatResult {
        val past = historyBySessionId[sessionId]?.toList().orEmpty()
        val historySize = past.size

        AppLogger.requestStart(TAG, sessionId, model, historySize, userMessage.length)

        return try {
            sendInternal(
                sessionId = sessionId,
                pastMessages = past,
                userMessage = userMessage,
                model = model,
                systemPrompt = systemPrompt,
                temperature = temperature,
                updateHistory = true
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Request failed", e)
            ChatResult.Error(e)
        }
    }

    override suspend fun sendWithContext(
        sessionId: String,
        contextMessages: List<Message>,
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double
    ): ChatResult {
        AppLogger.requestStart(TAG, sessionId, model, contextMessages.size, userMessage.length)
        return try {
            sendInternal(
                sessionId = sessionId,
                pastMessages = contextMessages,
                userMessage = userMessage,
                model = model,
                systemPrompt = systemPrompt,
                temperature = temperature,
                updateHistory = false
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Request failed", e)
            ChatResult.Error(e)
        }
    }

    override suspend fun sendWithTools(
        sessionId: String,
        contextMessages: List<Message>,
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double,
        tools: List<ApiToolDefinition>,
        toolExecutor: suspend (toolName: String, arguments: String) -> String,
        onToolCall: suspend (toolName: String, arguments: String, result: String, durationMs: Long) -> Unit
    ): ChatResult {
        if (tools.isEmpty()) {
            return sendWithContext(sessionId, contextMessages, userMessage, model, systemPrompt, temperature)
        }

        val apiKey = getApiKey()
        val trimmedUrl = ApiConfig.BASE_URL.trim().trimEnd(',', ' ')
        val maxIterations = 10
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var totalTokensAll = 0
        var totalTimeMs = 0L
        var useNativeTools = true

        val conversationMessages = buildList<ApiChatMessage> {
            add(ApiChatMessage(role = "system", content = systemPrompt))
            contextMessages.forEach { message ->
                val role = when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    else -> null
                }
                if (role != null) {
                    add(ApiChatMessage(role = role, content = message.content))
                }
            }
            add(ApiChatMessage(role = "user", content = userMessage))
        }.toMutableList()

        for (iteration in 0 until maxIterations) {
            val request = ApiChatRequest(
                model = model,
                messages = conversationMessages.toList(),
                max_tokens = ApiConfig.TOOL_CALLING_MAX_TOKENS,
                temperature = temperature,
                tools = if (useNativeTools) tools else null
            )

            val chatResponse: ApiChatResponse
            val iterTimeMs = measureTimeMillis {
                val response: HttpResponse = client.post(trimmedUrl) {
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
                    return ChatResult.Error(Exception("HTTP error ${response.status}: $errorBody"))
                }

                chatResponse = response.body()
            }

            totalTimeMs += iterTimeMs
            totalPromptTokens += chatResponse.usage?.prompt_tokens ?: 0
            totalCompletionTokens += chatResponse.usage?.completion_tokens ?: 0
            totalTokensAll += chatResponse.usage?.total_tokens ?: 0

            if (chatResponse.error != null) {
                return ChatResult.Error(Exception("API error: ${chatResponse.error.message}"))
            }

            val choice = chatResponse.choices?.firstOrNull()
                ?: return ChatResult.Error(Exception("No choices in API response"))

            val nativeToolCalls = choice.message?.tool_calls
            val content = choice.message?.content ?: ""

            if (!nativeToolCalls.isNullOrEmpty()) {
                conversationMessages.add(
                    ApiChatMessage(
                        role = "assistant",
                        content = choice.message?.content,
                        tool_calls = nativeToolCalls
                    )
                )
                for (tc in nativeToolCalls) {
                    val toolName = tc.function.name
                    val toolArgs = tc.function.arguments
                    var toolResult: String
                    val callTimeMs = measureTimeMillis {
                        toolResult = try {
                            toolExecutor(toolName, toolArgs)
                        } catch (e: Exception) {
                            "Error executing tool '$toolName': ${e.message}"
                        }
                    }
                    onToolCall(toolName, toolArgs, toolResult, callTimeMs)
                    conversationMessages.add(
                        ApiChatMessage(
                            role = "tool",
                            content = toolResult,
                            tool_call_id = tc.id,
                            name = toolName
                        )
                    )
                }
                continue
            }

            val textToolCall = parseTextToolCall(content)
            if (textToolCall != null) {
                useNativeTools = false
                conversationMessages.add(ApiChatMessage(role = "assistant", content = content))

                val toolName = textToolCall.first
                val toolArgs = textToolCall.second
                var toolResult: String
                val callTimeMs = measureTimeMillis {
                    toolResult = try {
                        toolExecutor(toolName, toolArgs)
                    } catch (e: Exception) {
                        "Error executing tool '$toolName': ${e.message}"
                    }
                }
                onToolCall(toolName, toolArgs, toolResult, callTimeMs)

                conversationMessages.add(
                    ApiChatMessage(
                        role = "user",
                        content = "[TOOL_RESULT name=\"$toolName\"]\n$toolResult\n[/TOOL_RESULT]\nИспользуй этот результат. Если нужен следующий инструмент — вызови его через [TOOL_CALL]. Если все шаги выполнены — дай финальный ответ пользователю без тегов [TOOL_CALL]."
                    )
                )
                continue
            }

            val cleanContent = content.ifBlank { "No response from model" }
            updateTokenAccumulator(sessionId, totalPromptTokens, totalCompletionTokens, totalTokensAll)
            val metrics = RequestMetrics(
                promptTokens = totalPromptTokens,
                completionTokens = totalCompletionTokens,
                totalTokens = totalTokensAll,
                responseTimeMs = totalTimeMs
            )
            return ChatResult.Success(Message.assistant(cleanContent), metrics)
        }

        updateTokenAccumulator(sessionId, totalPromptTokens, totalCompletionTokens, totalTokensAll)
        val metrics = RequestMetrics(
            promptTokens = totalPromptTokens,
            completionTokens = totalCompletionTokens,
            totalTokens = totalTokensAll,
            responseTimeMs = totalTimeMs
        )
        return ChatResult.Success(
            Message.assistant("Tool calling reached maximum iterations ($maxIterations)."),
            metrics
        )
    }

    private suspend fun sendInternal(
        sessionId: String,
        pastMessages: List<Message>,
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double,
        updateHistory: Boolean
    ): ChatResult {
        val apiKey = getApiKey()
        val trimmedUrl = ApiConfig.BASE_URL.trim().trimEnd(',', ' ')

        val apiMessages = buildList {
            add(ApiChatMessage(role = "system", content = systemPrompt))

            pastMessages.forEach { message ->
                val role =
                    when (message.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        else -> null
                    }
                if (role != null) {
                    add(ApiChatMessage(role = role, content = message.content))
                }
            }

            add(ApiChatMessage(role = "user", content = userMessage))
        }

        val request =
            ApiChatRequest(
                model = model,
                messages = apiMessages,
                max_tokens = ApiConfig.DEFAULT_MAX_TOKENS,
                temperature = temperature
            )

        FileLogger.section("API REQUEST")
        FileLogger.log(TAG, "Messages count: ${apiMessages.size}")
        apiMessages.forEachIndexed { index, msg ->
            val c = msg.content.orEmpty()
            val preview = if (c.length > 100) c.take(100) + "..." else c
            FileLogger.log(TAG, "  [$index] ${msg.role}: $preview")
        }
        if (apiMessages.any { it.role == "system" && it.content.orEmpty().contains("ПРЕДЫДУЩИЙ КОНТЕКСТ") }) {
            FileLogger.log(TAG, "*** CONTEXT SUMMARY IS INCLUDED IN SYSTEM PROMPT ***")
        }
        if (apiMessages.any { it.role == "system" && it.content.orEmpty().contains("[FACTS]") }) {
            FileLogger.log(TAG, "*** FACTS MEMORY IS INCLUDED IN SYSTEM PROMPT ***")
        }
        if (apiMessages.any { it.role == "system" && it.content.orEmpty().contains("--- MCP context ---") }) {
            FileLogger.log(TAG, "*** MCP CONTEXT IS INCLUDED IN SYSTEM PROMPT ***")
        } else if (apiMessages.any { it.role == "system" }) {
            FileLogger.log(TAG, "*** MCP CONTEXT NOT FOUND IN SYSTEM PROMPT ***")
        }

        var responseTimeMs = 0L
        val chatResponse: ApiChatResponse
        responseTimeMs = measureTimeMillis {
            val response: HttpResponse =
                client.post(trimmedUrl) {
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
                AppLogger.e(TAG, "HTTP error ${response.status}", null)

                if (errorBody.contains(CONTEXT_LENGTH_EXCEEDED, ignoreCase = true)) {
                    return handleContextLengthExceeded(sessionId, model, errorBody)
                }

                return ChatResult.Error(Exception("HTTP ошибка ${response.status}: $errorBody"))
            }

            chatResponse = response.body()
        }

        return when {
            chatResponse.error != null -> {
                val errorMsg = chatResponse.error.message ?: "Unknown error"
                val errorType = chatResponse.error.type
                val errorCode = chatResponse.error.code

                AppLogger.e(TAG, "API error: $errorMsg (type=$errorType, code=$errorCode)", null)

                if (errorType == CONTEXT_LENGTH_EXCEEDED || errorCode == CONTEXT_LENGTH_EXCEEDED) {
                    handleContextLengthExceeded(sessionId, model, errorMsg)
                } else {
                    ChatResult.Error(Exception("API ошибка: $errorMsg"))
                }
            }

            chatResponse.choices.isNullOrEmpty() -> {
                AppLogger.e(TAG, "Empty response from model", null)
                ChatResult.Error(Exception("Не удалось получить ответ от модели"))
            }

            else -> {
                val content =
                    chatResponse.choices.first().message?.content
                        ?: "Не удалось получить ответ от модели"

                if (updateHistory) {
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
                }

                val promptTokens = chatResponse.usage?.prompt_tokens ?: 0
                val completionTokens = chatResponse.usage?.completion_tokens ?: 0
                val totalTokens = chatResponse.usage?.total_tokens ?: 0

                updateTokenAccumulator(sessionId, promptTokens, completionTokens, totalTokens)

                val contextUsagePercent =
                    ModelContextLimits.getContextUsagePercent(
                        sessionTokenAccumulator[sessionId]?.totalTokens ?: 0,
                        model
                    )

                AppLogger.tokenInfo(
                    TAG,
                    sessionId,
                    promptTokens,
                    completionTokens,
                    totalTokens,
                    contextUsagePercent,
                    model
                )

                AppLogger.requestEnd(TAG, sessionId, responseTimeMs, totalTokens)

                val metrics =
                    RequestMetrics(
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        totalTokens = totalTokens,
                        responseTimeMs = responseTimeMs
                    )
                ChatResult.Success(Message.assistant(content), metrics)
            }
        }
    }

    private fun handleContextLengthExceeded(
        sessionId: String,
        model: String,
        errorDetails: String
    ): ChatResult.Error {
        AppLogger.w(TAG, "Context length exceeded for session $sessionId | $errorDetails")

        val historySize = historyBySessionId[sessionId]?.size ?: 0
        val suggestion = buildString {
            append("Превышен лимит контекста модели. ")
            append("Текущая история: $historySize сообщений. ")
            append("Рекомендуется: сбросить сессию или история будет автоматически сокращена.")
        }

        return ChatResult.Error(Exception(suggestion))
    }

    private fun updateTokenAccumulator(
        sessionId: String,
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int
    ) {
        sessionTokenAccumulator.compute(sessionId) { _, accumulator ->
            (accumulator ?: TokenAccumulator()).apply {
                update(promptTokens, completionTokens, totalTokens)
            }
        }
    }
}
