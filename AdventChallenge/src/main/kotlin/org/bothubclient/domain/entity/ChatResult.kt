package org.bothubclient.domain.entity

data class RequestMetrics(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val responseTimeMs: Long = 0
)

sealed class ChatResult {
    data class Success(
        val message: Message,
        val metrics: RequestMetrics = RequestMetrics()
    ) : ChatResult()
    data class Error(val exception: Exception) : ChatResult()
}
