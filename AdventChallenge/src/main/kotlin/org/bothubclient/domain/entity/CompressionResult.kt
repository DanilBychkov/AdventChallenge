package org.bothubclient.domain.entity

sealed class CompressionResult {
    data class Success(val newBlock: SummaryBlock) : CompressionResult()
    data class Partial(val newBlock: SummaryBlock, val warning: String) : CompressionResult()
    data class Failed(val error: Exception, val messagesToRecover: List<Message>) : CompressionResult()
    object NotNeeded : CompressionResult()

    val isSuccess: Boolean
        get() = this is Success || this is Partial

    val block: SummaryBlock?
        get() = when (this) {
            is Success -> newBlock
            is Partial -> newBlock
            else -> null
        }
}
