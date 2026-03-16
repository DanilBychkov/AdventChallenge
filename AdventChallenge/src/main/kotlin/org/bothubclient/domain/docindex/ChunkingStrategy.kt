package org.bothubclient.domain.docindex

enum class ChunkingStrategyType { FIXED_SIZE, STRUCTURAL }

interface ChunkingStrategy {
    val type: ChunkingStrategyType
    val displayName: String
    fun chunk(content: String, source: String, title: String): List<DocumentChunk>
}
