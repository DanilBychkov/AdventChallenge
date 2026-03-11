package org.bothubclient.infrastructure.scheduler

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withTimeout
import org.bothubclient.config.ApiConfig
import org.bothubclient.domain.repository.ReportGenerator
import org.bothubclient.infrastructure.api.ApiChatMessage
import org.bothubclient.infrastructure.api.ApiChatRequest
import org.bothubclient.infrastructure.api.ApiChatResponse
import org.bothubclient.infrastructure.logging.AppLogger

class LlmReportGenerator(
    private val client: HttpClient,
    private val getApiKey: () -> String,
    private val model: String = "gpt-4o-mini",
    private val timeoutMs: Long = 15_000L
) : ReportGenerator {

    companion object {
        private const val TAG = "LlmReportGenerator"

        private const val SYSTEM_PROMPT = """Ты — генератор кратких рекомендаций на русском языке.
Пользователь получил случайную активность от Bored API. Твоя задача:
1. Перевести активность на русский (если на английском).
2. Сформулировать краткую мотивирующую рекомендацию (1-2 предложения).
3. Ответ должен быть коротким и полезным.

Формат ответа: просто текст рекомендации, без JSON, без маркдауна."""
    }

    override suspend fun generateReport(activity: String): String {
        val apiKey = runCatching { getApiKey() }.getOrNull()?.trim().orEmpty()
        if (apiKey.isBlank()) {
            AppLogger.e(TAG, "Missing API key for report generation", null)
            return "Рекомендация: $activity"
        }

        return try {
            val request = ApiChatRequest(
                model = model,
                messages = listOf(
                    ApiChatMessage(role = "system", content = SYSTEM_PROMPT),
                    ApiChatMessage(role = "user", content = "Активность: $activity")
                ),
                max_tokens = 200,
                temperature = 0.3
            )

            val response = withTimeout(timeoutMs) {
                client.post(ApiConfig.BASE_URL) {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $apiKey")
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    }
                    setBody(request)
                }
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw IllegalStateException("LLM API error ${response.status}: $errorBody")
            }

            val chatResponse: ApiChatResponse = response.body()
            if (chatResponse.error != null) {
                throw IllegalStateException("LLM API error: ${chatResponse.error.message}")
            }

            val content = chatResponse.choices?.firstOrNull()?.message?.content?.trim()
            if (content.isNullOrBlank()) {
                AppLogger.e(TAG, "Empty LLM response, using fallback", null)
                "Рекомендация: $activity"
            } else {
                AppLogger.i(TAG, "Generated report: ${content.take(80)}...")
                content
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "LLM report generation failed, using fallback", e)
            "Рекомендация: $activity"
        }
    }
}
