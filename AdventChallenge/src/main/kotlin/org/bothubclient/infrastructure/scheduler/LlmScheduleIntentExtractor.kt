package org.bothubclient.infrastructure.scheduler

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bothubclient.application.usecase.ScheduleIntent
import org.bothubclient.config.ApiConfig
import org.bothubclient.infrastructure.api.ApiChatMessage
import org.bothubclient.infrastructure.api.ApiChatRequest
import org.bothubclient.infrastructure.api.ApiChatResponse
import org.bothubclient.infrastructure.logging.AppLogger

class LlmScheduleIntentExtractor(
    private val client: HttpClient,
    private val getApiKey: () -> String,
    private val model: String = "gpt-4o-mini",
    private val timeoutMs: Long = 5_000L
) {
    companion object {
        private const val TAG = "LlmScheduleIntentExtractor"

        private const val SYSTEM_PROMPT =
            """Ты — классификатор намерений. Определи, хочет ли пользователь настроить периодическую фоновую задачу ("чем заняться", "активности", "рекомендации").

Верни ТОЛЬКО JSON (без markdown, без пояснений):
{
  "intent": "SCHEDULE_BORED_REPORT" или "NONE",
  "intervalMinutes": число или null,
  "enabled": true/false или null,
  "confidence": число от 0 до 1
}

Правила:
- Если пользователь просит присылать/подсказывать/напоминать чем заняться с интервалом → SCHEDULE_BORED_REPORT
- Если просит остановить/отключить фоновую задачу → SCHEDULE_BORED_REPORT с enabled=false
- Если сообщение не связано с расписанием → NONE
- "каждый час" = 60 минут, "каждые полчаса" = 30 минут
- confidence >= 0.75 для уверенных решений"""
    }

    @Serializable
    private data class IntentResponse(
        val intent: String = "NONE",
        val intervalMinutes: Int? = null,
        val enabled: Boolean? = null,
        val confidence: Double = 0.0
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun extract(userMessage: String): ScheduleIntent {
        val apiKey = runCatching { getApiKey() }.getOrNull()?.trim().orEmpty()
        if (apiKey.isBlank()) {
            return ScheduleIntent(ScheduleIntent.NONE, null, null, 0.0)
        }

        return try {
            val request = ApiChatRequest(
                model = model,
                messages = listOf(
                    ApiChatMessage(role = "system", content = SYSTEM_PROMPT),
                    ApiChatMessage(role = "user", content = userMessage)
                ),
                max_tokens = 150,
                temperature = 0.1
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
                return ScheduleIntent(ScheduleIntent.NONE, null, null, 0.0)
            }

            val chatResponse: ApiChatResponse = response.body()
            val rawContent = chatResponse.choices?.firstOrNull()?.message?.content?.trim()
                ?: return ScheduleIntent(ScheduleIntent.NONE, null, null, 0.0)

            val jsonStr = extractJsonObject(rawContent)
                ?: return ScheduleIntent(ScheduleIntent.NONE, null, null, 0.0)

            val parsed = json.decodeFromString(IntentResponse.serializer(), jsonStr)

            ScheduleIntent(
                intent = parsed.intent,
                intervalMinutes = parsed.intervalMinutes,
                enabled = parsed.enabled,
                confidence = parsed.confidence
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "LLM intent extraction failed", e)
            ScheduleIntent(ScheduleIntent.NONE, null, null, 0.0)
        }
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end < 0 || end <= start) return null
        return text.substring(start, end + 1).trim()
    }
}
