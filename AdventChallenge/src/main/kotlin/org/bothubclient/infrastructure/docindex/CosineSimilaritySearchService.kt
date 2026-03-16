package org.bothubclient.infrastructure.docindex

import org.bothubclient.config.DocumentIndexConfig
import org.bothubclient.domain.docindex.DocumentIndexRepository
import org.bothubclient.domain.docindex.DocumentSearchResult
import org.bothubclient.domain.docindex.DocumentSearchService
import org.bothubclient.domain.docindex.EmbeddingService
import org.bothubclient.infrastructure.logging.AppLogger
import kotlin.math.sqrt

class CosineSimilaritySearchService(
    private val indexRepository: DocumentIndexRepository,
    private val embeddingService: EmbeddingService
) : DocumentSearchService {

    private val tag = "CosineSimilaritySearchService"

    override suspend fun search(
        query: String,
        projectHash: String,
        topK: Int,
        minSimilarity: Float
    ): List<DocumentSearchResult> {
        val index = indexRepository.load(projectHash)
        if (index == null) {
            AppLogger.w(tag, "No index found for project $projectHash")
            return emptyList()
        }

        val queryEmbedding = embeddingService.embed(query)

        val allScored = index.chunks
            .filter { it.embedding != null }
            .map { chunk ->
                val similarity = cosineSimilarity(queryEmbedding, chunk.embedding!!)
                DocumentSearchResult(chunk = chunk, similarity = similarity)
            }
            .sortedByDescending { it.similarity }

        val topScore = allScored.firstOrNull()?.similarity ?: 0f
        val results = allScored.filter { it.similarity >= minSimilarity }.take(topK)

        AppLogger.d(tag, "Search for '${query.take(50)}': ${results.size}/${allScored.size} results above $minSimilarity (topScore=$topScore)")
        return results
    }

    companion object {
        fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
            if (a.size != b.size || a.isEmpty()) return 0f

            var dotProduct = 0.0
            var normA = 0.0
            var normB = 0.0

            for (i in a.indices) {
                dotProduct += a[i].toDouble() * b[i].toDouble()
                normA += a[i].toDouble() * a[i].toDouble()
                normB += b[i].toDouble() * b[i].toDouble()
            }

            val denominator = sqrt(normA) * sqrt(normB)
            if (denominator == 0.0) return 0f

            return (dotProduct / denominator).toFloat()
        }
    }
}
