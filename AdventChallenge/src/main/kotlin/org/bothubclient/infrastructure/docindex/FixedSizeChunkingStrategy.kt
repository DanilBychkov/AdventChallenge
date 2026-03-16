package org.bothubclient.infrastructure.docindex

import org.bothubclient.config.DocumentIndexConfig
import org.bothubclient.domain.docindex.ChunkMetadata
import org.bothubclient.domain.docindex.ChunkingStrategy
import org.bothubclient.domain.docindex.ChunkingStrategyType
import org.bothubclient.domain.docindex.DocumentChunk
import java.util.UUID

class FixedSizeChunkingStrategy(
    private val chunkSizeTokens: Int = DocumentIndexConfig.DEFAULT_CHUNK_SIZE,
    private val overlapTokens: Int = DocumentIndexConfig.DEFAULT_OVERLAP
) : ChunkingStrategy {

    override val type: ChunkingStrategyType = ChunkingStrategyType.FIXED_SIZE
    override val displayName: String = "Fixed Size ($chunkSizeTokens tokens)"

    override fun chunk(content: String, source: String, title: String): List<DocumentChunk> {
        if (content.isBlank()) return emptyList()

        val chunkSizeChars = chunkSizeTokens * 4
        val overlapChars = overlapTokens * 4
        val stepChars = (chunkSizeChars - overlapChars).coerceAtLeast(1)

        val chunks = mutableListOf<DocumentChunk>()
        var offset = 0
        var chunkIndex = 0

        while (offset < content.length) {
            val end = (offset + chunkSizeChars).coerceAtMost(content.length)
            val chunkText = content.substring(offset, end)

            if (chunkText.isNotBlank()) {
                chunks.add(
                    DocumentChunk(
                        chunkId = UUID.randomUUID().toString(),
                        content = chunkText,
                        metadata = ChunkMetadata(
                            source = source,
                            title = title,
                            section = "",
                            chunkIndex = chunkIndex,
                            charOffset = offset,
                            tokenCount = chunkText.length / 4
                        )
                    )
                )
                chunkIndex++
            }

            offset += stepChars
        }

        return chunks
    }
}
