package org.bothubclient.infrastructure.context

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.bothubclient.config.ApiConfig
import org.bothubclient.domain.context.SummaryGenerator
import org.bothubclient.domain.context.SummaryResult
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.SummaryBlock
import org.bothubclient.domain.entity.SummaryStatus
import org.bothubclient.infrastructure.api.ApiChatMessage
import org.bothubclient.infrastructure.api.ApiChatRequest
import org.bothubclient.infrastructure.api.ApiChatResponse
import org.bothubclient.infrastructure.logging.AppLogger

class LlmSummaryGenerator(
    private val client: HttpClient,
    private val getApiKey: () -> String,
    private val summaryModel: String = "gpt-4o-mini"
) : SummaryGenerator {

    companion object {
        private const val TAG = "LlmSummaryGenerator"
    }

    override fun getSummaryPrompt(messages: List<Message>, maxTokens: Int): String = """
Твоя задача: создать краткое содержание диалога для сохранения контекста.

ПРАВИЛА:
1. Сохраняй ключевые решения, выводы и договоренности
2. Упоминай важные имена, даты, цифры
3. Сохраняй хронологию событий
4. Игнорируй приветствия и filler-фразы
5. Максимум $maxTokens токенов
6. Формат: связный текст без маркированного списка

СООБЩЕНИЯ ДЛЯ СЖАТИЯ:
${messages.joinToString("\n") { "[${it.role}]: ${it.content}" }}

КРАТКОЕ СОДЕРЖАНИЕ:
""".trimIndent()

    override suspend fun generateSummary(
        messages: List<Message>,
        maxTokens: Int
    ): SummaryResult {
        return try {
            val apiKey = getApiKey()
            val prompt = getSummaryPrompt(messages, maxTokens)

            val request = ApiChatRequest(
                model = summaryModel,
                messages = listOf(
                    ApiChatMessage(
                        role = "system",
                        content = "Ты помощник для создания кратких содержаний диалогов. Отвечай только кратким содержанием, без вводных фраз."
                    ),
                    ApiChatMessage(role = "user", content = prompt)
                ),
                max_tokens = maxTokens,
                temperature = 0.3
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
                AppLogger.e(TAG, "Summary generation failed: ${response.status} - $errorBody", null)
                return SummaryResult.Error(
                    Exception("Summary generation failed: ${response.status}")
                )
            }

            val chatResponse = response.body<ApiChatResponse>()

            if (chatResponse.error != null) {
                return SummaryResult.Error(
                    Exception("API error: ${chatResponse.error.message}")
                )
            }

            val summaryContent = chatResponse.choices?.firstOrNull()?.message?.content
                ?: return SummaryResult.Error(Exception("Empty summary response"))

            val block = SummaryBlock(
                originalMessageCount = messages.size,
                originalMessages = messages,
                summary = summaryContent.trim(),
                estimatedTokens = chatResponse.usage?.total_tokens ?: (summaryContent.length / 4),
                status = SummaryStatus.COMPLETED
            )

            AppLogger.i(TAG, "Summary generated: ${messages.size} messages -> ${block.estimatedTokens} tokens")

            SummaryResult.Success(block)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Summary generation error", e)
            SummaryResult.Error(e)
        }
    }
}
