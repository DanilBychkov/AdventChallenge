package org.bothubclient.domain.docindex

data class DocumentSearchResult(
    val chunk: DocumentChunk,
    val similarity: Float
)
