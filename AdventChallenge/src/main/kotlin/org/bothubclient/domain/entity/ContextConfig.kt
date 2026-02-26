package org.bothubclient.domain.entity

data class ContextConfig(
    val keepLastN: Int = 10,
    val compressionBlockSize: Int = 5,
    val maxSummaryBlocks: Int = 5,
    val enableAutoCompression: Boolean = true,
    val compressionThreshold: Float = 0.7f,
    val summaryMaxTokens: Int = 200
) {
    companion object {
        val DEFAULT = ContextConfig()

        val CONSERVATIVE = ContextConfig(
            keepLastN = 6,
            compressionBlockSize = 4,
            maxSummaryBlocks = 3
        )

        val AGGRESSIVE = ContextConfig(
            keepLastN = 4,
            compressionBlockSize = 3,
            maxSummaryBlocks = 8
        )
    }

    fun shouldCompress(historySize: Int): Boolean =
        (historySize - keepLastN) >= compressionBlockSize

    fun withKeepLastN(value: Int): ContextConfig =
        copy(keepLastN = value.coerceIn(2, 50))

    fun withCompressionBlockSize(value: Int): ContextConfig =
        copy(compressionBlockSize = value.coerceIn(2, 20))

    fun withMaxSummaryBlocks(value: Int): ContextConfig =
        copy(maxSummaryBlocks = value.coerceIn(1, 20))

    fun withAutoCompression(enabled: Boolean): ContextConfig =
        copy(enableAutoCompression = enabled)

    override fun toString(): String =
        "ContextConfig(keepLastN=$keepLastN, blockSize=$compressionBlockSize, maxBlocks=$maxSummaryBlocks, auto=$enableAutoCompression)"
}
