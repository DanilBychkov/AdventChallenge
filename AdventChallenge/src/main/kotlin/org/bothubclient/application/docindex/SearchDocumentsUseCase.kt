package org.bothubclient.application.docindex

import org.bothubclient.config.DocumentIndexConfig
import org.bothubclient.domain.docindex.DocumentSearchResult
import org.bothubclient.domain.docindex.DocumentSearchService

class SearchDocumentsUseCase(
    private val searchService: DocumentSearchService
) {

    suspend fun execute(
        query: String,
        projectHash: String,
        topK: Int = DocumentIndexConfig.DEFAULT_TOP_K,
        minSimilarity: Float = DocumentIndexConfig.DEFAULT_MIN_SIMILARITY
    ): List<DocumentSearchResult> {
        return searchService.search(
            query = query,
            projectHash = projectHash,
            topK = topK,
            minSimilarity = minSimilarity
        )
    }
}
