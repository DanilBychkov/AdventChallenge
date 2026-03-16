package org.bothubclient.domain.docindex

interface EmbeddingService {
    suspend fun embed(texts: List<String>): List<List<Float>>
    suspend fun embed(text: String): List<Float>
}
