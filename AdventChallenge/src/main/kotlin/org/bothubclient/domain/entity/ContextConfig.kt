package org.bothubclient.domain.entity

data class ContextConfig(
    val strategy: ContextStrategy = ContextStrategy.SLIDING_WINDOW,
    val keepLastN: Int = 10,
    val compressionBlockSize: Int = 5,
    val maxSummaryBlocks: Int = 5,
    val enableAutoCompression: Boolean = true,
    val compressionThreshold: Float = 0.7f,
    val summaryMaxTokens: Int = 200,
    val includeAgentPrimer: Boolean = true,
    val enableFactsMemory: Boolean = true,
    val maxFacts: Int = 24
) {
    companion object {
        val DEFAULT = ContextConfig()

        val CONSERVATIVE =
            ContextConfig(
                strategy = ContextStrategy.SLIDING_WINDOW,
                keepLastN = 6,
                compressionBlockSize = 4,
                maxSummaryBlocks = 3
            )

        val AGGRESSIVE =
            ContextConfig(
                strategy = ContextStrategy.SLIDING_WINDOW,
                keepLastN = 4,
                compressionBlockSize = 3,
                maxSummaryBlocks = 8
            )
    }

    fun shouldCompress(historySize: Int): Boolean =
        (historySize - keepLastN) >= compressionBlockSize

    fun withKeepLastN(value: Int): ContextConfig = copy(keepLastN = value.coerceIn(2, 50))

    fun withStrategy(value: ContextStrategy): ContextConfig = copy(strategy = value)

    fun withCompressionBlockSize(value: Int): ContextConfig =
        copy(compressionBlockSize = value.coerceIn(2, 20))

    fun withMaxSummaryBlocks(value: Int): ContextConfig =
        copy(maxSummaryBlocks = value.coerceIn(1, 20))

    fun withAutoCompression(enabled: Boolean): ContextConfig = copy(enableAutoCompression = enabled)

    override fun toString(): String =
        "ContextConfig(strategy=$strategy, keepLastN=$keepLastN, blockSize=$compressionBlockSize, maxBlocks=$maxSummaryBlocks, auto=$enableAutoCompression, facts=$enableFactsMemory)"
}
