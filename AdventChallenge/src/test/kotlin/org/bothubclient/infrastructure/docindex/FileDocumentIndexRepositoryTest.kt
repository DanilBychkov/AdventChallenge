package org.bothubclient.infrastructure.docindex

import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.docindex.ChunkMetadata
import org.bothubclient.domain.docindex.DocumentChunk
import org.bothubclient.domain.docindex.StoredDocumentIndex
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileDocumentIndexRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var repository: FileDocumentIndexRepository

    @BeforeEach
    fun setUp() {
        repository = FileDocumentIndexRepository(baseDir = tempDir.resolve("doc-index"))
    }

    @Test
    fun save_thenLoad_roundTrip() = runTest {
        // Arrange
        val projectHash = "abc123def456"
        val index = StoredDocumentIndex(
            model = "text-embedding-3-small",
            dimensions = 1536,
            strategy = "FIXED_SIZE",
            createdAt = "2024-01-01T00:00:00Z",
            sourceDirectory = "/test/dir",
            chunks = listOf(
                DocumentChunk(
                    chunkId = "chunk-1",
                    content = "Hello world content",
                    metadata = ChunkMetadata(
                        source = "/test/file.txt",
                        title = "file.txt",
                        section = "intro",
                        chunkIndex = 0,
                        charOffset = 0,
                        tokenCount = 5
                    ),
                    embedding = listOf(0.1f, 0.2f, 0.3f)
                )
            )
        )

        // Act
        repository.save(projectHash, index)
        val loaded = repository.load(projectHash)

        // Assert
        assertNotNull(loaded)
        assertEquals(index.model, loaded.model)
        assertEquals(index.dimensions, loaded.dimensions)
        assertEquals(index.strategy, loaded.strategy)
        assertEquals(index.sourceDirectory, loaded.sourceDirectory)
        assertEquals(1, loaded.chunks.size)
        assertEquals("chunk-1", loaded.chunks[0].chunkId)
        assertEquals("Hello world content", loaded.chunks[0].content)
        assertEquals(listOf(0.1f, 0.2f, 0.3f), loaded.chunks[0].embedding)
        assertEquals("intro", loaded.chunks[0].metadata.section)
    }

    @Test
    fun load_nonExistentIndex_returnsNull() = runTest {
        // Act
        val result = repository.load("aabbccddee11")

        // Assert
        assertNull(result)
    }

    @Test
    fun exists_afterSave_returnsTrue() = runTest {
        // Arrange
        val projectHash = "ee1122334455"
        repository.save(projectHash, makeMinimalIndex())

        // Act & Assert
        assertTrue(repository.exists(projectHash))
    }

    @Test
    fun exists_beforeSave_returnsFalse() = runTest {
        // Act & Assert
        assertFalse(repository.exists("ff00112233aa"))
    }

    @Test
    fun delete_afterSave_existsReturnsFalse() = runTest {
        // Arrange
        val projectHash = "dead0beef001"
        repository.save(projectHash, makeMinimalIndex())
        assertTrue(repository.exists(projectHash))

        // Act
        repository.delete(projectHash)

        // Assert
        assertFalse(repository.exists(projectHash))
    }

    @Test
    fun delete_nonExistent_doesNotThrow() = runTest {
        // Act & Assert - should not throw
        repository.delete("dead0beef002")
    }

    @Test
    fun load_afterDelete_returnsNull() = runTest {
        // Arrange
        val projectHash = "dead0beef003"
        repository.save(projectHash, makeMinimalIndex())
        repository.delete(projectHash)

        // Act
        val result = repository.load(projectHash)

        // Assert
        assertNull(result)
    }

    @Test
    fun save_overwritesExistingIndex() = runTest {
        // Arrange
        val projectHash = "dead0beef004"
        val index1 = makeMinimalIndex(sourceDir = "/dir1")
        val index2 = makeMinimalIndex(sourceDir = "/dir2")

        // Act
        repository.save(projectHash, index1)
        repository.save(projectHash, index2)
        val loaded = repository.load(projectHash)

        // Assert
        assertNotNull(loaded)
        assertEquals("/dir2", loaded.sourceDirectory)
    }

    @Test
    fun save_multipleProjects_isolatedCorrectly() = runTest {
        // Arrange
        val index1 = makeMinimalIndex(sourceDir = "/project1")
        val index2 = makeMinimalIndex(sourceDir = "/project2")

        // Act
        repository.save("aa00000001aa", index1)
        repository.save("aa00000002aa", index2)

        // Assert
        assertEquals("/project1", repository.load("aa00000001aa")!!.sourceDirectory)
        assertEquals("/project2", repository.load("aa00000002aa")!!.sourceDirectory)
    }

    @Test
    fun save_indexWithNoChunks_roundTrips() = runTest {
        // Arrange
        val projectHash = "dead0beef005"
        val index = StoredDocumentIndex(
            model = "test",
            dimensions = 3,
            strategy = "STRUCTURAL",
            createdAt = "2024-01-01",
            sourceDirectory = "/empty",
            chunks = emptyList()
        )

        // Act
        repository.save(projectHash, index)
        val loaded = repository.load(projectHash)

        // Assert
        assertNotNull(loaded)
        assertTrue(loaded.chunks.isEmpty())
    }

    private fun makeMinimalIndex(sourceDir: String = "/test"): StoredDocumentIndex {
        return StoredDocumentIndex(
            model = "test-model",
            dimensions = 3,
            strategy = "FIXED_SIZE",
            createdAt = "2024-01-01",
            sourceDirectory = sourceDir,
            chunks = listOf(
                DocumentChunk(
                    chunkId = "c1",
                    content = "content",
                    metadata = ChunkMetadata("src", "title", "", 0, 0, 2),
                    embedding = listOf(0.1f, 0.2f, 0.3f)
                )
            )
        )
    }
}
