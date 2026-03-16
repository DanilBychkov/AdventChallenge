package org.bothubclient.infrastructure.docindex

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bothubclient.config.DocumentIndexConfig
import org.bothubclient.domain.docindex.EmbeddingService
import org.bothubclient.infrastructure.logging.AppLogger

@Serializable
data class EmbeddingRequest(
    val model: String,
    val input: List<String>,
    @SerialName("encoding_format") val encodingFormat: String = "float"
)

@Serializable
data class EmbeddingResponse(
    val data: List<EmbeddingData>,
    val model: String,
    val usage: EmbeddingUsage? = null
)

@Serializable
data class EmbeddingData(
    val embedding: List<Float>,
    val index: Int
)

@Serializable
data class EmbeddingUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

class BothubEmbeddingService(
    private val client: HttpClient,
    private val getApiKey: () -> String
) : EmbeddingService {

    private val tag = "BothubEmbeddingService"
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun embed(texts: List<String>): List<List<Float>> {
        if (texts.isEmpty()) return emptyList()

        val maxCharsPerText = 32_000 // ~8000 tokens conservative limit for text-embedding-3-small
        val sanitized = texts.map { text ->
            if (text.length > maxCharsPerText) {
                AppLogger.w(tag, "Truncating oversized text (${text.length} chars) to $maxCharsPerText chars")
                text.take(maxCharsPerText)
            } else text
        }

        val allEmbeddings = mutableListOf<List<Float>>()

        for (batch in sanitized.chunked(DocumentIndexConfig.EMBEDDING_BATCH_SIZE)) {
            val batchResult = requestEmbeddings(batch)
            allEmbeddings.addAll(batchResult)
        }

        return allEmbeddings
    }

    override suspend fun embed(text: String): List<Float> {
        return embed(listOf(text)).firstOrNull()
            ?: throw IllegalStateException("Empty embedding response for single text")
    }

    private suspend fun requestEmbeddings(texts: List<String>): List<List<Float>> {
        val request = EmbeddingRequest(
            model = DocumentIndexConfig.EMBEDDING_MODEL,
            input = texts
        )
        val requestBody = json.encodeToString(EmbeddingRequest.serializer(), request)

        var lastException: Exception? = null

        for (attempt in 0 until DocumentIndexConfig.EMBEDDING_RETRY_MAX) {
            try {
                val response: HttpResponse = client.post(DocumentIndexConfig.EMBEDDINGS_URL) {
                    header("Authorization", "Bearer ${getApiKey()}")
                    setBody(TextContent(requestBody, ContentType.Application.Json))
                }

                when (response.status.value) {
                    200 -> {
                        val responseBody = response.bodyAsText()
                        val parsed = json.decodeFromString(EmbeddingResponse.serializer(), responseBody)
                        return parsed.data
                            .sortedBy { it.index }
                            .map { it.embedding }
                    }
                    401 -> {
                        throw IllegalStateException("Invalid API key (401 Unauthorized)")
                    }
                    429 -> {
                        val delayMs = DocumentIndexConfig.EMBEDDING_RETRY_BASE_DELAY_MS * (1L shl attempt)
                        AppLogger.w(tag, "Rate limited (429), retrying in ${delayMs}ms (attempt ${attempt + 1})")
                        delay(delayMs)
                        lastException = RuntimeException("Rate limited (429)")
                    }
                    in 500..503 -> {
                        val delayMs = DocumentIndexConfig.EMBEDDING_RETRY_BASE_DELAY_MS * (1L shl attempt)
                        AppLogger.w(tag, "Server error (${response.status.value}), retrying in ${delayMs}ms (attempt ${attempt + 1})")
                        delay(delayMs)
                        lastException = RuntimeException("Server error: ${response.status.value}")
                    }
                    else -> {
                        val body = runCatching { response.bodyAsText() }.getOrDefault("")
                        AppLogger.e(tag, "Embedding API error ${response.status.value}: $body")
                        throw RuntimeException("Embedding API error (HTTP ${response.status.value})")
                    }
                }
            } catch (e: IllegalStateException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < DocumentIndexConfig.EMBEDDING_RETRY_MAX - 1) {
                    val delayMs = DocumentIndexConfig.EMBEDDING_RETRY_BASE_DELAY_MS * (1L shl attempt)
                    AppLogger.w(tag, "Request failed, retrying in ${delayMs}ms: ${e.message}")
                    delay(delayMs)
                }
            }
        }

        throw lastException ?: RuntimeException("Embedding request failed after ${DocumentIndexConfig.EMBEDDING_RETRY_MAX} retries")
    }
}
