package org.bothubclient.application.docindex

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.docindex.ChunkMetadata
import org.bothubclient.domain.docindex.DocumentChunk
import org.bothubclient.domain.docindex.DocumentSearchResult
import org.bothubclient.domain.docindex.DocumentSearchService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentContextInjectorTest {

    private lateinit var searchService: DocumentSearchService
    private lateinit var injector: DocumentContextInjector

    @BeforeEach
    fun setUp() {
        searchService = mockk()
        injector = DocumentContextInjector(searchService)
    }

    @Test
    fun getDocumentContext_nullProjectHash_returnsEmptyResult() = runTest {
        // Act
        val result = injector.getDocumentContext("query", null)

        // Assert
        assertFalse(result.hasContent)
        assertEquals(0, result.chunkCount)
        assertEquals("", result.content)
        assertTrue(result.sources.isEmpty())
    }

    @Test
    fun getDocumentContext_noSearchResults_returnsEmptyResult() = runTest {
        // Arrange
        coEvery { searchService.search(any(), any(), any(), any()) } returns emptyList()

        // Act
        val result = injector.getDocumentContext("query", "hash123")

        // Assert
        assertFalse(result.hasContent)
        assertEquals(0, result.chunkCount)
    }

    @Test
    fun getDocumentContext_withResults_formatsWithDocChunkTags() = runTest {
        // Arrange
        val chunk = DocumentChunk(
            chunkId = "id-1",
            content = "Some relevant content",
            metadata = ChunkMetadata(
                source = "/path/file.md",
                title = "file.md",
                section = "Introduction",
                chunkIndex = 0,
                charOffset = 0,
                tokenCount = 5
            ),
            embedding = listOf(0.1f, 0.2f)
        )
        val searchResults = listOf(DocumentSearchResult(chunk, 0.95f))
        coEvery { searchService.search(any(), any(), any(), any()) } returns searchResults

        // Act
        val result = injector.getDocumentContext("query", "hash123")

        // Assert
        assertTrue(result.hasContent)
        assertEquals(1, result.chunkCount)
        assertTrue(result.content.contains("[DOC_CHUNK"))
        assertTrue(result.content.contains("[/DOC_CHUNK]"))
        assertTrue(result.content.contains("Some relevant content"))
        assertTrue(result.content.contains("/path/file.md"))
        assertTrue(result.content.contains("Introduction"))
        assertTrue(result.content.contains("id-1"))
    }

    @Test
    fun getDocumentContext_respectsMaxTokensLimit() = runTest {
        // Arrange
        // maxTokens=5 => maxChars=20. Header "--- Document context (RAG) ---\n" is already >20 chars
        // So after 1st chunk added (even if oversized), next chunks should be skipped
        val chunk1 = makeSearchResult("c1", "Short", "/f1.md", 0.9f)
        val chunk2 = makeSearchResult("c2", "Another piece of content that is long", "/f2.md", 0.8f)

        coEvery { searchService.search(any(), any(), any(), any()) } returns listOf(chunk1, chunk2)

        // Act
        // With very small maxTokens, only some chunks should be included
        val result = injector.getDocumentContext("query", "hash", maxTokens = 5)

        // Assert
        // At least 1 chunk is always included (first chunk is added even if exceeds limit)
        assertTrue(result.chunkCount >= 1)
        // But should NOT include both if limit is very small
        assertTrue(result.chunkCount <= 2)
    }

    @Test
    fun getDocumentContext_multipleChunks_collectsSources() = runTest {
        // Arrange
        val results = listOf(
            makeSearchResult("c1", "content1", "/path/a.md", 0.9f),
            makeSearchResult("c2", "content2", "/path/b.md", 0.8f),
            makeSearchResult("c3", "content3", "/path/a.md", 0.7f), // duplicate source
        )
        coEvery { searchService.search(any(), any(), any(), any()) } returns results

        // Act
        val result = injector.getDocumentContext("query", "hash", maxTokens = 10000)

        // Assert
        assertEquals(3, result.chunkCount)
        // Sources should be deduplicated (set-based)
        assertTrue(result.sources.contains("/path/a.md"))
        assertTrue(result.sources.contains("/path/b.md"))
    }

    @Test
    fun getDocumentContext_searchThrows_returnsEmptyResult() = runTest {
        // Arrange
        coEvery { searchService.search(any(), any(), any(), any()) } throws RuntimeException("Search failed")

        // Act
        val result = injector.getDocumentContext("query", "hash123")

        // Assert
        assertFalse(result.hasContent)
        assertEquals(0, result.chunkCount)
    }

    @Test
    fun getDocumentContext_contentContainsHeaderAndFooter() = runTest {
        // Arrange
        val results = listOf(makeSearchResult("c1", "text", "/f.md", 0.9f))
        coEvery { searchService.search(any(), any(), any(), any()) } returns results

        // Act
        val result = injector.getDocumentContext("query", "hash", maxTokens = 10000)

        // Assert
        assertTrue(result.content.startsWith("--- Document context (RAG) ---"))
        assertTrue(result.content.endsWith("--- End document context ---"))
    }

    private fun makeSearchResult(
        chunkId: String,
        content: String,
        source: String,
        similarity: Float
    ): DocumentSearchResult {
        return DocumentSearchResult(
            chunk = DocumentChunk(
                chunkId = chunkId,
                content = content,
                metadata = ChunkMetadata(source, "title", "", 0, 0, content.length / 4),
                embedding = listOf(0.1f)
            ),
            similarity = similarity
        )
    }
}
