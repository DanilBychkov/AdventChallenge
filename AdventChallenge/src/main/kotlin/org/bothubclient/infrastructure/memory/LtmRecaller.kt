package org.bothubclient.infrastructure.memory

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.bothubclient.config.ApiConfig
import org.bothubclient.domain.entity.WmCategory
import org.bothubclient.domain.memory.MemoryItem
import org.bothubclient.infrastructure.api.ApiChatMessage
import org.bothubclient.infrastructure.api.ApiChatRequest
import org.bothubclient.infrastructure.api.ApiChatResponse
import org.bothubclient.infrastructure.logging.AppLogger

class LtmRecaller(
    private val client: HttpClient,
    private val getApiKey: () -> String,
    private val timeoutMs: Long = 8_000L
) {
    companion object {
        private const val TAG = "LtmRecaller"
        private const val MAX_ITEMS = 15
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    data class LtmItemDto(
        val category: String,
        val key: String,
        val value: String,
        val confidence: Float
    )

    @Serializable
    data class RecallResponse(val relevant_keys: List<String> = emptyList())

    private fun systemPrompt(): String =
        """
Ты — движок семантического поиска по памяти пользователя.

ЗАДАЧА: выбрать записи из ltmItems, релевантные для ответа на userMessage.

ПРАВИЛА:
1. ВСЕГДА включай категорию USER_INFO (identity факты: имя, компания, роль)
2. Включай семантически связанные записи
3. НЕ включай нерелевантное
4. Максимум 15 записей

ФОРМАТ ОТВЕТА (строго JSON):
{"relevant_keys": ["key1", "key2"]}
""".trimIndent()

    suspend fun recall(
        userMessage: String,
        ltmItems: List<MemoryItem>,
        model: String
    ): List<MemoryItem> {
        if (ltmItems.isEmpty()) return emptyList()

        val apiKey = runCatching { getApiKey() }.getOrNull()?.trim().orEmpty()
        if (apiKey.isBlank()) return fallbackToUserInfo(ltmItems)

        return try {
            val itemsJson =
                json.encodeToString(
                    ListSerializer(LtmItemDto.serializer()),
                    ltmItems.map {
                        LtmItemDto(
                            category = it.category.name,
                            key = it.key,
                            value = it.entry.value,
                            confidence = it.entry.confidence
                        )
                    }
                )

            val userPrompt =
                """
userMessage:
${userMessage.trim()}

ltmItems (JSON):
$itemsJson
""".trimIndent()

            val request =
                ApiChatRequest(
                    model = model,
                    messages =
                        listOf(
                            ApiChatMessage(role = "system", content = systemPrompt()),
                            ApiChatMessage(role = "user", content = userPrompt)
                        ),
                    max_tokens = 300,
                    temperature = 0.1
                )

            val response =
                withTimeout(timeoutMs) {
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
                throw IllegalStateException("LTM recall failed: ${response.status} - $errorBody")
            }

            val chatResponse: ApiChatResponse = response.body()
            if (chatResponse.error != null) {
                throw IllegalStateException("API error: ${chatResponse.error.message}")
            }

            val rawContent =
                chatResponse.choices?.firstOrNull()?.message?.content?.trim()
                    ?: return fallbackToUserInfo(ltmItems)

            val jsonObj = extractJsonObject(rawContent) ?: return fallbackToUserInfo(ltmItems)
            val parsed = json.decodeFromString(RecallResponse.serializer(), jsonObj)

            val keySet = parsed.relevant_keys.asSequence().filter { it.isNotBlank() }.toSet()

            val selected = ltmItems.asSequence().filter { it.key in keySet }.toList()
            val userInfo = ltmItems.filter { it.category == WmCategory.USER_INFO }

            val merged =
                (selected + userInfo)
                    .distinctBy { Triple(it.category, it.key, it.entry.value) }
                    .take(MAX_ITEMS)

            AppLogger.i(TAG, "LLM recall: ${merged.size}/${ltmItems.size} items, model=$model")
            merged
        } catch (e: Exception) {
            AppLogger.e(TAG, "LLM recall failed, fallback to USER_INFO", e)
            fallbackToUserInfo(ltmItems)
        }
    }

    private fun fallbackToUserInfo(items: List<MemoryItem>): List<MemoryItem> =
        items.filter { it.category == WmCategory.USER_INFO }.take(MAX_ITEMS)

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end < 0 || end <= start) return null
        return text.substring(start, end + 1).trim()
    }
}
