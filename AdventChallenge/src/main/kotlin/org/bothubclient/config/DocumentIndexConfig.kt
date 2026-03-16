package org.bothubclient.config

object DocumentIndexConfig {
    const val EMBEDDINGS_URL = "https://bothub.chat/api/v2/openai/v1/embeddings"
    const val EMBEDDING_MODEL = "text-embedding-3-small"
    const val EMBEDDING_DIMENSIONS = 1536
    const val DEFAULT_CHUNK_SIZE = 512
    const val DEFAULT_OVERLAP = 64
    const val STRUCTURAL_MAX_CHUNK_SIZE = 1024
    const val MAX_FILE_SIZE_BYTES = 10_485_760L
    val ALLOWED_EXTENSIONS = setOf(".txt", ".md")
    const val DEFAULT_TOP_K = 5
    const val DEFAULT_MIN_SIMILARITY = 0.3f
    const val MAX_DOC_CONTEXT_TOKENS = 2048
    const val EMBEDDING_BATCH_SIZE = 20
    const val EMBEDDING_RETRY_MAX = 3
    const val EMBEDDING_RETRY_BASE_DELAY_MS = 1000L
    const val INDEX_BASE_DIR = "doc-index"
}
