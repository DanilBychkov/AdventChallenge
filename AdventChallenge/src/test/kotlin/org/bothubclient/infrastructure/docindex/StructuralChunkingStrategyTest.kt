package org.bothubclient.infrastructure.docindex

import org.bothubclient.domain.docindex.ChunkingStrategyType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StructuralChunkingStrategyTest {

    @Test
    fun chunk_emptyText_returnsEmptyList() {
        // Arrange
        val strategy = StructuralChunkingStrategy(maxChunkSizeTokens = 1024)

        // Act
        val result = strategy.chunk("", "doc.md", "doc.md")

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun chunk_blankText_returnsEmptyList() {
        // Arrange
        val strategy = StructuralChunkingStrategy(maxChunkSizeTokens = 1024)

        // Act
        val result = strategy.chunk("   \n  \t  ", "doc.md", "doc.md")

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun chunk_markdownWithHeaders_splitsOnHeaders() {
        // Arrange
        val strategy = StructuralChunkingStrategy(maxChunkSizeTokens = 1024)
        val markdown = """
            |# Header One
            |Content of section one.
            |
            |## Header Two
            |Content of section two.
        """.trimMargin()

        // Act
        val result = strategy.chunk(markdown, "readme.md", "readme.md")

        // Assert
        assertTrue(result.size >= 2, "Expected at least 2 chunks for 2 headers, got ${result.size}")
        assertTrue(result.any { it.metadata.section.contains("Header One") })
        assertTrue(result.any { it.metadata.section.contains("Header Two") })
    }

    @Test
    fun chunk_markdownWithPreambleBeforeFirstHeader_includesPreamble() {
        // Arrange
        val strategy = StructuralChunkingStrategy(maxChunkSizeTokens = 1024)
        val markdown = """
            |This is a preamble before any headers.
            |
            |# First Header
            |Content here.
        """.trimMargin()

        // Act
        val result = strategy.chunk(markdown, "doc.md", "doc.md")

        // Assert
        assertTrue(result.size >= 2, "Expected preamble + header section, got ${result.size}")
        assertTrue(result[0].content.contains("preamble"))
    }

    @Test
    fun chunk_txtFile_splitsByDoubleNewlines() {
        // Arrange
        val strategy = StructuralChunkingStrategy(maxChunkSizeTokens = 1024)
        val text = """
            |First paragraph content.
            |
            |Second paragraph content.
            |
            |Third paragraph content.
        """.trimMargin()

        // Act
        val result = strategy.chunk(text, "notes.txt", "notes.txt")

        // Assert
        assertEquals(3, result.size)
        assertTrue(result[0].content.contains("First paragraph"))
        assertTrue(result[1].content.contains("Second paragraph"))
        assertTrue(result[2].content.contains("Third paragraph"))
    }

    @Test
    fun chunk_txtFile_sectionMetadataIsEmpty() {
        // Arrange
        val strategy = StructuralChunkingStrategy(maxChunkSizeTokens = 1024)
        val text = "First paragraph.\n\nSecond paragraph."

        // Act
        val result = strategy.chunk(text, "notes.txt", "notes.txt")

        // Assert
        // For .txt files, splitByDoubleNewlines returns sections with empty header
        result.forEach { chunk ->
            assertEquals("", chunk.metadata.section)
        }
    }

    @Test
    fun chunk_longMarkdownSection_isFurtherSplit() {
        // Arrange
        // maxChunkSizeTokens=10 => maxChunkSizeChars=40
        val strategy = StructuralChunkingStrategy(maxChunkSizeTokens = 10)
        val longContent = "a".repeat(200)
        val markdown = "# Big Section\n$longContent"

        // Act
        val result = strategy.chunk(markdown, "doc.md", "doc.md")

        // Assert
        assertTrue(result.size > 1, "Long section should be split into multiple chunks, got ${result.size}")
        // All sub-chunks should retain the section header
        result.forEach { chunk ->
            assertEquals("# Big Section", chunk.metadata.section)
        }
    }

    @Test
    fun chunk_markdownSectionFitsWithinLimit_notSplit() {
        // Arrange
        val strategy = StructuralChunkingStrategy(maxChunkSizeTokens = 1024)
        val markdown = "# Short Section\nShort content."

        // Act
        val result = strategy.chunk(markdown, "doc.md", "doc.md")

        // Assert
        assertEquals(1, result.size)
    }

    @Test
    fun chunk_metadataSourceAndTitleArePreserved() {
        // Arrange
        val strategy = StructuralChunkingStrategy(maxChunkSizeTokens = 1024)

        // Act
        val result = strategy.chunk("Some text content", "path/to/file.txt", "file.txt")

        // Assert
        assertEquals(1, result.size)
        assertEquals("path/to/file.txt", result[0].metadata.source)
        assertEquals("file.txt", result[0].metadata.title)
    }

    @Test
    fun chunk_metadataChunkIndex_incrementsAcrossSections() {
        // Arrange
        val strategy = StructuralChunkingStrategy(maxChunkSizeTokens = 1024)
        val markdown = "# A\nContent A.\n\n# B\nContent B.\n\n# C\nContent C."

        // Act
        val result = strategy.chunk(markdown, "doc.md", "doc.md")

        // Assert
        for (i in result.indices) {
            assertEquals(i, result[i].metadata.chunkIndex)
        }
    }

    @Test
    fun chunk_tokenCount_isContentLengthDividedByFour() {
        // Arrange
        val strategy = StructuralChunkingStrategy(maxChunkSizeTokens = 1024)
        val text = "Hello world test."

        // Act
        val result = strategy.chunk(text, "doc.txt", "doc.txt")

        // Assert
        assertEquals(1, result.size)
        val expectedTokens = result[0].content.length / 4
        assertEquals(expectedTokens, result[0].metadata.tokenCount)
    }

    @Test
    fun chunk_chunkIdsAreUnique() {
        // Arrange
        val strategy = StructuralChunkingStrategy(maxChunkSizeTokens = 1024)
        val markdown = "# A\nContent.\n\n# B\nContent.\n\n# C\nContent."

        // Act
        val result = strategy.chunk(markdown, "doc.md", "doc.md")

        // Assert
        val ids = result.map { it.chunkId }.toSet()
        assertEquals(result.size, ids.size)
    }

    @Test
    fun type_returnsStructural() {
        // Arrange & Act & Assert
        assertEquals(ChunkingStrategyType.STRUCTURAL, StructuralChunkingStrategy().type)
    }

    @Test
    fun chunk_markdownWithMultipleLevelHeaders_allRecognized() {
        // Arrange
        val strategy = StructuralChunkingStrategy(maxChunkSizeTokens = 1024)
        val markdown = """
            |# H1
            |Content 1.
            |## H2
            |Content 2.
            |### H3
            |Content 3.
        """.trimMargin()

        // Act
        val result = strategy.chunk(markdown, "doc.md", "doc.md")

        // Assert
        assertTrue(result.size >= 3, "Expected at least 3 sections for 3 headers, got ${result.size}")
    }

    @Test
    fun chunk_markdownHeaderOnlyNoBody_producesChunk() {
        // Arrange
        val strategy = StructuralChunkingStrategy(maxChunkSizeTokens = 1024)
        val markdown = "# Only Header"

        // Act
        val result = strategy.chunk(markdown, "doc.md", "doc.md")

        // Assert
        // The header-only section may produce a chunk depending on implementation
        // The implementation: firstNewline = -1 => sectionText.trim() to ""
        // fullSection = "# Only Header\n" => isNotBlank = true => 1 chunk
        assertTrue(result.isNotEmpty())
    }
}
