package org.bothubclient.domain.docindex

data class DocumentChunk(
    val chunkId: String,
    val content: String,
    val metadata: ChunkMetadata,
    val embedding: List<Float>? = null
)

data class ChunkMetadata(
    val source: String,
    val title: String,
    val section: String,
    val chunkIndex: Int,
    val charOffset: Int,
    val tokenCount: Int
)
