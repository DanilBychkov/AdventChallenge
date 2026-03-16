package org.bothubclient.domain.docindex

enum class IndexingState { IDLE, SCANNING, CHUNKING, EMBEDDING, SAVING, DONE, ERROR }

data class IndexingProgress(
    val state: IndexingState = IndexingState.IDLE,
    val totalFiles: Int = 0,
    val processedFiles: Int = 0,
    val totalChunks: Int = 0,
    val processedChunks: Int = 0,
    val errors: List<IndexingError> = emptyList(),
    val elapsedMs: Long = 0
)

data class IndexingError(
    val file: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
