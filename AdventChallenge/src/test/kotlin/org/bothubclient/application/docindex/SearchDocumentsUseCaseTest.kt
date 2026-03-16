package org.bothubclient.application.docindex

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.docindex.ChunkMetadata
import org.bothubclient.domain.docindex.DocumentChunk
import org.bothubclient.domain.docindex.DocumentSearchResult
import org.bothubclient.domain.docindex.DocumentSearchService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchDocumentsUseCaseTest {

    private lateinit var searchService: DocumentSearchService
    private lateinit var useCase: SearchDocumentsUseCase

    @BeforeEach
    fun setUp() {
        searchService = mockk()
        useCase = SearchDocumentsUseCase(searchService)
    }

    @Test
    fun execute_delegatesToSearchService() = runTest {
        // Arrange
        val expected = listOf(
            DocumentSearchResult(
                chunk = DocumentChunk(
                    chunkId = "1",
                    content = "test",
                    metadata = ChunkMetadata("src", "title", "", 0, 0, 1),
                    embedding = listOf(0.1f)
                ),
                similarity = 0.9f
            )
        )
        coEvery { searchService.search("query", "hash", 5, 0.3f) } returns expected

        // Act
        val result = useCase.execute("query", "hash")

        // Assert
        assertEquals(1, result.size)
        assertEquals("1", result[0].chunk.chunkId)
        coVerify { searchService.search("query", "hash", 5, 0.3f) }
    }

    @Test
    fun execute_passesCustomTopKAndMinSimilarity() = runTest {
        // Arrange
        coEvery { searchService.search("q", "h", 10, 0.5f) } returns emptyList()

        // Act
        val result = useCase.execute("q", "h", topK = 10, minSimilarity = 0.5f)

        // Assert
        assertTrue(result.isEmpty())
        coVerify { searchService.search("q", "h", 10, 0.5f) }
    }

    @Test
    fun execute_emptyResults_returnsEmptyList() = runTest {
        // Arrange
        coEvery { searchService.search(any(), any(), any(), any()) } returns emptyList()

        // Act
        val result = useCase.execute("query", "hash")

        // Assert
        assertTrue(result.isEmpty())
    }
}
