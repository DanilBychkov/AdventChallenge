package org.bothubclient.domain.context

import org.bothubclient.domain.entity.CompressionResult
import org.bothubclient.domain.entity.ContextConfig
import org.bothubclient.domain.entity.Message

interface ContextCompressor {
    fun needsCompression(sessionId: String, config: ContextConfig): Boolean

    suspend fun compress(
        sessionId: String,
        config: ContextConfig
    ): CompressionResult

    fun rollback(sessionId: String, blockId: String): List<Message>?
}
