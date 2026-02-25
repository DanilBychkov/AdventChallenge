package org.bothubclient.domain.entity

data class SessionTokenStatistics(
    val sessionId: String,
    val totalPromptTokens: Int,
    val totalCompletionTokens: Int,
    val totalTokens: Int,
    val messageCount: Int,
    val estimatedCostRub: Double?,
    val lastRequestTokens: Int,
    val lastResponseTokens: Int,
    val contextLimit: Int,
    val contextUsagePercent: Float
) {
    val isApproachingLimit: Boolean
        get() = contextUsagePercent > 80f

    val isCriticalLimit: Boolean
        get() = contextUsagePercent > 95f

    val remainingTokens: Int
        get() = contextLimit - totalTokens

    companion object {
        val EMPTY = SessionTokenStatistics(
            sessionId = "",
            totalPromptTokens = 0,
            totalCompletionTokens = 0,
            totalTokens = 0,
            messageCount = 0,
            estimatedCostRub = null,
            lastRequestTokens = 0,
            lastResponseTokens = 0,
            contextLimit = 128000,
            contextUsagePercent = 0f
        )
    }
}
