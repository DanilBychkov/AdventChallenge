package org.bothubclient.infrastructure.context

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.bothubclient.config.ApiConfig
import org.bothubclient.infrastructure.api.ApiChatMessage
import org.bothubclient.infrastructure.api.ApiChatRequest
import org.bothubclient.infrastructure.api.ApiChatResponse
import org.bothubclient.infrastructure.logging.AppLogger

class LlmFactsExtractor(
    private val client: HttpClient,
    private val getApiKey: () -> String,
    private val model: String = "gpt-4o-mini",
    private val timeoutMs: Long = 5_000L
) {

    companion object {
        private const val TAG = "LlmFactsExtractor"
        private const val MIN_CONFIDENCE = 0.6
        private const val MAX_FACTS_PER_CALL = 10
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    data class Fact(
        val category: String,
        val key: String,
        val value: String,
        val confidence: Double
    )

    @Serializable
    data class MergeAction(
        val action: String,
        val category: String? = null,
        val key: String? = null,
        @SerialName("previous_value") val previousValue: String? = null,
        @SerialName("new_value") val newValue: String? = null,
        val group: String? = null
    )

    @Serializable
    data class Response(
        val facts: List<Fact> = emptyList(),
        val groups: Map<String, Map<String, String>> = emptyMap(),
        @SerialName("merge_actions") val mergeActions: List<MergeAction> = emptyList()
    )

    private fun systemPrompt(): String =
        """
Ты — движок извлечения фактов из одного сообщения пользователя.

ЦЕЛЬ:
1) Извлечь факты из userMessage.
2) Учитывать existingGroups как текущую память.
3) Вернуть только JSON (без Markdown, без пояснений).

КАТЕГОРИИ (используй эти значения в поле category):
- identity: user_name, company, role, contact
- project: name, description, scope
- requirements: features, integrations, platforms, sso, compliance
- constraints: budget, timeline, team_size, resources
- preferences: language, timezone, locale
- technical: stack, architecture, api
- business: sla, audit, security, mvp_timeline
- timeline: deadlines, milestones
- other: всё остальное

ПРАВИЛА:
- Извлекай максимум 10 фактов.
- Каждый факт: {category, key, value, confidence}.
- Фильтруй факты с confidence < 0.6 (не включай их в ответ).
- Не выдумывай значения. Если факт не указан явно — пропусти.
- Если факт конфликтует с existingGroups (тот же key, другое value), укажи merge_actions с previous_value и new_value.

ФОРМАТ ОТВЕТА (строго JSON):
{
  "facts": [
    {"category": "identity", "key": "user_name", "value": "Алексей", "confidence": 0.95}
  ],
  "groups": {
    "identity": {"user_name": "Алексей"}
  },
  "merge_actions": [
    {"action": "create_group", "group": "identity"},
    {"action": "update_fact", "category": "constraints", "key": "budget", "previous_value": "$3000", "new_value": "$5000"}
  ]
}
""".trimIndent()

    suspend fun extractFacts(
        userMessage: String,
        existingGroups: Map<String, Map<String, String>>
    ): Response {
        val apiKey = runCatching { getApiKey() }.getOrNull()?.trim().orEmpty()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Missing API key")
        }

        val existingGroupsJson =
            json.encodeToString(
                MapSerializer(
                    String.serializer(),
                    MapSerializer(String.serializer(), String.serializer())
                ),
                existingGroups
            )

        val userPrompt =
            """
userMessage:
${userMessage.trim()}

existingGroups (JSON):
$existingGroupsJson
""".trimIndent()

        return try {
            val request =
                ApiChatRequest(
                    model = model,
                    messages =
                        listOf(
                            ApiChatMessage(
                                role = "system",
                                content = systemPrompt()
                            ),
                            ApiChatMessage(role = "user", content = userPrompt)
                        ),
                    max_tokens = 600,
                    temperature = 0.1
                )

            val response =
                withTimeout(timeoutMs) {
                    client.post(ApiConfig.BASE_URL) {
                        headers {
                            append(HttpHeaders.Authorization, "Bearer $apiKey")
                            append(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString()
                            )
                            append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                        }
                        setBody(request)
                    }
                }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw IllegalStateException(
                    "Facts extraction failed: ${response.status} - $errorBody"
                )
            }

            val chatResponse: ApiChatResponse = response.body()
            if (chatResponse.error != null) {
                throw IllegalStateException("API error: ${chatResponse.error.message}")
            }

            val rawContent =
                chatResponse.choices?.firstOrNull()?.message?.content?.trim()
                    ?: throw IllegalStateException("Empty facts response")

            val jsonObject =
                extractJsonObject(rawContent)
                    ?: throw IllegalStateException("Invalid JSON in facts response")

            val parsed = json.decodeFromString(Response.serializer(), jsonObject)

            val filteredFacts =
                parsed.facts
                    .asSequence()
                    .filter { it.confidence >= MIN_CONFIDENCE }
                    .filter {
                        it.key.isNotBlank() &&
                                it.value.isNotBlank() &&
                                it.category.isNotBlank()
                    }
                    .take(MAX_FACTS_PER_CALL)
                    .toList()

            val cleaned =
                parsed.copy(facts = filteredFacts, mergeActions = parsed.mergeActions.take(50))

            AppLogger.i(
                TAG,
                "LLM facts extracted: facts=${cleaned.facts.size}, actions=${cleaned.mergeActions.size}"
            )
            cleaned
        } catch (e: Exception) {
            AppLogger.e(TAG, "LLM facts extraction error", e)
            throw e
        }
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end < 0 || end <= start) return null
        return text.substring(start, end + 1).trim()
    }
}
