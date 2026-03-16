package org.bothubclient.domain.docindex

data class StoredDocumentIndex(
    val version: Int = 1,
    val model: String,
    val dimensions: Int,
    val strategy: String,
    val createdAt: String,
    val sourceDirectory: String,
    val chunks: List<DocumentChunk>
)

interface DocumentIndexRepository {
    suspend fun save(projectHash: String, index: StoredDocumentIndex)
    suspend fun load(projectHash: String): StoredDocumentIndex?
    suspend fun delete(projectHash: String)
    suspend fun exists(projectHash: String): Boolean
}
