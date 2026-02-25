package org.bothubclient.config

object ModelContextLimits {
    private val limits = mapOf(
        "gpt-5.2" to 400000,
        "gpt-5" to 400000,
        "gpt-5-pro" to 400000,
        "gpt-5-image" to 400000,
        "gpt-5.1" to 400000,
        "gpt-4" to 1048576,
        "gpt-4-turbo" to 128000,
        "gpt-4o" to 128000,
        "gpt-4o-mini" to 128000,
        "o4-mini-deep-research" to 100000,
        "claude-3-5-sonnet" to 200000,
        "claude-sonnet-4" to 200000,
        "gemini-2.5-pro-preview" to 1048576,
        "gemini-2.5-pro" to 1048576,
        "gemini-2.5-flash" to 1048576,
        "gemini-2.0-flash" to 1048576,
        "gemini-2.0-flash-lite-001" to 1048576,
        "gpt-3.5-turbo" to 16385,
        "deepseek-chat" to 64000,
        "deepseek-reasoner" to 64000,
        "llama-3.3-70b" to 128000,
        "llama-3-70b-instruct" to 8192,
        "llama-4-scout" to 128000,
        "grok-4.1-fast" to 128000,
        "grok-3" to 128000,
        "o3" to 200000,
        "o3-mini" to 200000,
        "o4-mini" to 200000
    )

    fun getContextLimit(model: String): Int = limits[model] ?: 128000

    fun getContextUsagePercent(currentTokens: Int, model: String): Float =
        if (getContextLimit(model) > 0) {
            (currentTokens.toFloat() / getContextLimit(model)) * 100
        } else {
            0f
        }

    fun isApproachingLimit(currentTokens: Int, model: String, threshold: Float = 0.8f): Boolean =
        if (getContextLimit(model) > 0) {
            currentTokens.toFloat() / getContextLimit(model) > threshold
        } else {
            false
        }

    fun getRemainingTokens(currentTokens: Int, model: String): Int =
        getContextLimit(model) - currentTokens

    fun getTokensForThreshold(model: String, threshold: Float = 0.8f): Int =
        (getContextLimit(model) * threshold).toInt()
}
