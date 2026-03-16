package org.bothubclient.infrastructure.docindex

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.docindex.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CosineSimilaritySearchServiceTest {

    private lateinit var indexRepository: DocumentIndexRepository
    private lateinit var embeddingService: EmbeddingService
    private lateinit var searchService: CosineSimilaritySearchService

    @BeforeEach
    fun setUp() {
        indexRepository = mockk()
        embeddingService = mockk()
        searchService = CosineSimilaritySearchService(indexRepository, embeddingService)
    }

    // --- cosineSimilarity static method tests ---

    @Test
    fun cosineSimilarity_parallelVectors_returnsOne() {
        // Arrange
        val a = listOf(1f, 2f, 3f)
        val b = listOf(2f, 4f, 6f)

        // Act
        val result = CosineSimilaritySearchService.cosineSimilarity(a, b)

        // Assert
        assertTrue(abs(result - 1.0f) < 0.0001f, "Expected ~1.0 for parallel vectors, got $result")
    }

    @Test
    fun cosineSimilarity_orthogonalVectors_returnsZero() {
        // Arrange
        val a = listOf(1f, 0f)
        val b = listOf(0f, 1f)

        // Act
        val result = CosineSimilaritySearchService.cosineSimilarity(a, b)

        // Assert
        assertTrue(abs(result) < 0.0001f, "Expected ~0.0 for orthogonal vectors, got $result")
    }

    @Test
    fun cosineSimilarity_oppositeVectors_returnsNegativeOne() {
        // Arrange
        val a = listOf(1f, 0f, 0f)
        val b = listOf(-1f, 0f, 0f)

        // Act
        val result = CosineSimilaritySearchService.cosineSimilarity(a, b)

        // Assert
        assertTrue(abs(result - (-1.0f)) < 0.0001f, "Expected ~-1.0 for opposite vectors, got $result")
    }

    @Test
    fun cosineSimilarity_zeroVector_returnsZero() {
        // Arrange
        val a = listOf(0f, 0f, 0f)
        val b = listOf(1f, 2f, 3f)

        // Act
        val result = CosineSimilaritySearchService.cosineSimilarity(a, b)

        // Assert
        assertEquals(0f, result)
    }

    @Test
    fun cosineSimilarity_bothZeroVectors_returnsZero() {
        // Arrange
        val a = listOf(0f, 0f)
        val b = listOf(0f, 0f)

        // Act
        val result = CosineSimilaritySearchService.cosineSimilarity(a, b)

        // Assert
        assertEquals(0f, result)
    }

    @Test
    fun cosineSimilarity_differentSizes_returnsZero() {
        // Arrange
        val a = listOf(1f, 2f)
        val b = listOf(1f, 2f, 3f)

        // Act
        val result = CosineSimilaritySearchService.cosineSimilarity(a, b)

        // Assert
        assertEquals(0f, result)
    }

    @Test
    fun cosineSimilarity_emptyVectors_returnsZero() {
        // Act
        val result = CosineSimilaritySearchService.cosineSimilarity(emptyList(), emptyList())

        // Assert
        assertEquals(0f, result)
    }

    // --- search method tests ---

    @Test
    fun search_noIndexFound_returnsEmptyList() = runTest {
        // Arrange
        coEvery { indexRepository.load("hash123") } returns null

        // Act
        val results = searchService.search("query", "hash123")

        // Assert
        assertTrue(results.isEmpty())
    }

    @Test
    fun search_indexWithNoEmbeddings_returnsEmptyList() = runTest {
        // Arrange
        val index = StoredDocumentIndex(
            model = "test",
            dimensions = 3,
            strategy = "FIXED_SIZE",
            createdAt = "2024-01-01",
            sourceDirectory = "/test",
            chunks = listOf(
                DocumentChunk(
                    chunkId = "1",
                    content = "text",
                    metadata = ChunkMetadata("src", "title", "", 0, 0, 10),
                    embedding = null
                )
            )
        )
        coEvery { indexRepository.load("hash123") } returns index
        coEvery { embeddingService.embed("query") } returns listOf(1f, 0f, 0f)

        // Act
        val results = searchService.search("query", "hash123")

        // Assert
        assertTrue(results.isEmpty())
    }

    @Test
    fun search_returnsTopKResults() = runTest {
        // Arrange
        val queryEmbedding = listOf(1f, 0f, 0f)
        val chunks = listOf(
            makeChunk("1", listOf(1f, 0f, 0f)),    // similarity = 1.0
            makeChunk("2", listOf(0.9f, 0.1f, 0f)), // high similarity
            makeChunk("3", listOf(0.8f, 0.2f, 0f)), // medium similarity
            makeChunk("4", listOf(0.5f, 0.5f, 0f)), // lower similarity
        )
        val index = makeIndex(chunks)

        coEvery { indexRepository.load("hash") } returns index
        coEvery { embeddingService.embed("query") } returns queryEmbedding

        // Act
        val results = searchService.search("query", "hash", topK = 2, minSimilarity = 0f)

        // Assert
        assertEquals(2, results.size)
        // Results should be sorted descending by similarity
        assertTrue(results[0].similarity >= results[1].similarity)
    }

    @Test
    fun search_filtersByMinSimilarity() = runTest {
        // Arrange
        val queryEmbedding = listOf(1f, 0f, 0f)
        val chunks = listOf(
            makeChunk("1", listOf(1f, 0f, 0f)),   // similarity = 1.0
            makeChunk("2", listOf(0f, 1f, 0f)),   // similarity = 0.0 (orthogonal)
        )
        val index = makeIndex(chunks)

        coEvery { indexRepository.load("hash") } returns index
        coEvery { embeddingService.embed("query") } returns queryEmbedding

        // Act
        val results = searchService.search("query", "hash", topK = 10, minSimilarity = 0.5f)

        // Assert
        assertEquals(1, results.size)
        assertEquals("1", results[0].chunk.chunkId)
    }

    @Test
    fun search_resultsSortedDescendingBySimilarity() = runTest {
        // Arrange
        val queryEmbedding = listOf(1f, 0f, 0f)
        val chunks = listOf(
            makeChunk("low", listOf(0.5f, 0.5f, 0f)),
            makeChunk("high", listOf(1f, 0f, 0f)),
            makeChunk("mid", listOf(0.8f, 0.2f, 0f)),
        )
        val index = makeIndex(chunks)

        coEvery { indexRepository.load("hash") } returns index
        coEvery { embeddingService.embed("query") } returns queryEmbedding

        // Act
        val results = searchService.search("query", "hash", topK = 10, minSimilarity = 0f)

        // Assert
        for (i in 0 until results.size - 1) {
            assertTrue(results[i].similarity >= results[i + 1].similarity)
        }
    }

    // --- helpers ---

    private fun makeChunk(id: String, embedding: List<Float>): DocumentChunk {
        return DocumentChunk(
            chunkId = id,
            content = "content-$id",
            metadata = ChunkMetadata(
                source = "src",
                title = "title",
                section = "",
                chunkIndex = 0,
                charOffset = 0,
                tokenCount = 10
            ),
            embedding = embedding
        )
    }

    private fun makeIndex(chunks: List<DocumentChunk>): StoredDocumentIndex {
        return StoredDocumentIndex(
            model = "test-model",
            dimensions = 3,
            strategy = "FIXED_SIZE",
            createdAt = "2024-01-01",
            sourceDirectory = "/test",
            chunks = chunks
        )
    }
}
