package org.bothubclient.infrastructure.docindex

import org.bothubclient.domain.docindex.ChunkingStrategyType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixedSizeChunkingStrategyTest {

    @Test
    fun chunk_emptyText_returnsEmptyList() {
        // Arrange
        val strategy = FixedSizeChunkingStrategy(chunkSizeTokens = 100, overlapTokens = 10)

        // Act
        val result = strategy.chunk("", "source.txt", "title")

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun chunk_blankText_returnsEmptyList() {
        // Arrange
        val strategy = FixedSizeChunkingStrategy(chunkSizeTokens = 100, overlapTokens = 10)

        // Act
        val result = strategy.chunk("   \n\t  ", "source.txt", "title")

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun chunk_textShorterThanChunkSize_returnsSingleChunk() {
        // Arrange
        val strategy = FixedSizeChunkingStrategy(chunkSizeTokens = 100, overlapTokens = 10)
        val shortText = "Hello world"

        // Act
        val result = strategy.chunk(shortText, "source.txt", "title")

        // Assert
        assertEquals(1, result.size)
        assertEquals(shortText, result[0].content)
    }

    @Test
    fun chunk_textExactlyChunkSize_returnsSingleChunk() {
        // Arrange
        // chunkSizeTokens = 10 => chunkSizeChars = 40
        val strategy = FixedSizeChunkingStrategy(chunkSizeTokens = 10, overlapTokens = 0)
        val text = "a".repeat(40)

        // Act
        val result = strategy.chunk(text, "source.txt", "title")

        // Assert
        assertEquals(1, result.size)
        assertEquals(40, result[0].content.length)
    }

    @Test
    fun chunk_textLargerThanChunkSize_noOverlap_producesCorrectChunks() {
        // Arrange
        // chunkSizeTokens=10 => chunkSizeChars=40, overlapTokens=0 => overlapChars=0, stepChars=40
        val strategy = FixedSizeChunkingStrategy(chunkSizeTokens = 10, overlapTokens = 0)
        val text = "a".repeat(100) // 100 chars => 3 chunks (40+40+20)

        // Act
        val result = strategy.chunk(text, "source.txt", "title")

        // Assert
        assertEquals(3, result.size)
        assertEquals(40, result[0].content.length)
        assertEquals(40, result[1].content.length)
        assertEquals(20, result[2].content.length)
    }

    @Test
    fun chunk_withOverlap_chunksOverlapCorrectly() {
        // Arrange
        // chunkSizeTokens=10 => chunkSizeChars=40, overlapTokens=5 => overlapChars=20, stepChars=20
        val strategy = FixedSizeChunkingStrategy(chunkSizeTokens = 10, overlapTokens = 5)
        val text = "abcdefghij".repeat(6) // 60 chars

        // Act
        val result = strategy.chunk(text, "source.txt", "title")

        // Assert
        // With stepChars=20 and chunkSizeChars=40:
        // chunk0: offset=0, end=40
        // chunk1: offset=20, end=60
        // chunk2: offset=40, end=60 (20 chars)
        assertEquals(3, result.size)

        // Verify overlap: chunk0 ends at 40, chunk1 starts at 20
        // The last 20 chars of chunk0 should equal the first 20 chars of chunk1
        val chunk0Tail = result[0].content.substring(20)
        val chunk1Head = result[1].content.substring(0, 20)
        assertEquals(chunk0Tail, chunk1Head)
    }

    @Test
    fun chunk_metadataSource_isPreserved() {
        // Arrange
        val strategy = FixedSizeChunkingStrategy(chunkSizeTokens = 100, overlapTokens = 0)
        val text = "Some content here"

        // Act
        val result = strategy.chunk(text, "/path/to/file.md", "MyFile.md")

        // Assert
        assertEquals(1, result.size)
        assertEquals("/path/to/file.md", result[0].metadata.source)
        assertEquals("MyFile.md", result[0].metadata.title)
    }

    @Test
    fun chunk_metadataSectionIsEmpty() {
        // Arrange
        val strategy = FixedSizeChunkingStrategy(chunkSizeTokens = 100, overlapTokens = 0)

        // Act
        val result = strategy.chunk("content", "source.txt", "title")

        // Assert
        assertEquals("", result[0].metadata.section)
    }

    @Test
    fun chunk_metadataChunkIndex_incrementsCorrectly() {
        // Arrange
        val strategy = FixedSizeChunkingStrategy(chunkSizeTokens = 5, overlapTokens = 0)
        // chunkSizeChars=20, stepChars=20
        val text = "a".repeat(60) // 3 chunks

        // Act
        val result = strategy.chunk(text, "source.txt", "title")

        // Assert
        assertEquals(0, result[0].metadata.chunkIndex)
        assertEquals(1, result[1].metadata.chunkIndex)
        assertEquals(2, result[2].metadata.chunkIndex)
    }

    @Test
    fun chunk_metadataCharOffset_isCorrect() {
        // Arrange
        val strategy = FixedSizeChunkingStrategy(chunkSizeTokens = 5, overlapTokens = 0)
        // chunkSizeChars=20, stepChars=20
        val text = "a".repeat(60)

        // Act
        val result = strategy.chunk(text, "source.txt", "title")

        // Assert
        assertEquals(0, result[0].metadata.charOffset)
        assertEquals(20, result[1].metadata.charOffset)
        assertEquals(40, result[2].metadata.charOffset)
    }

    @Test
    fun chunk_metadataTokenCount_isContentLengthDividedByFour() {
        // Arrange
        val strategy = FixedSizeChunkingStrategy(chunkSizeTokens = 10, overlapTokens = 0)
        val text = "a".repeat(50) // chunkSizeChars=40, chunk0=40 chars, chunk1=10 chars

        // Act
        val result = strategy.chunk(text, "source.txt", "title")

        // Assert
        assertEquals(10, result[0].metadata.tokenCount) // 40/4
        assertEquals(2, result[1].metadata.tokenCount)   // 10/4 = 2 (integer division)
    }

    @Test
    fun chunk_chunkIdsAreUnique() {
        // Arrange
        val strategy = FixedSizeChunkingStrategy(chunkSizeTokens = 5, overlapTokens = 0)
        val text = "a".repeat(80)

        // Act
        val result = strategy.chunk(text, "source.txt", "title")

        // Assert
        val ids = result.map { it.chunkId }.toSet()
        assertEquals(result.size, ids.size)
    }

    @Test
    fun type_returnsFixedSize() {
        // Arrange
        val strategy = FixedSizeChunkingStrategy()

        // Act & Assert
        assertEquals(ChunkingStrategyType.FIXED_SIZE, strategy.type)
    }

    @Test
    fun displayName_containsTokenCount() {
        // Arrange
        val strategy = FixedSizeChunkingStrategy(chunkSizeTokens = 256, overlapTokens = 32)

        // Act & Assert
        assertTrue(strategy.displayName.contains("256"))
    }
}
