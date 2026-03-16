package org.bothubclient.domain.docindex

interface DocumentSearchService {
    suspend fun search(
        query: String,
        projectHash: String,
        topK: Int = 5,
        minSimilarity: Float = 0.3f
    ): List<DocumentSearchResult>
}
