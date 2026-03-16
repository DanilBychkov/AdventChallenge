package org.bothubclient.application.docindex

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.docindex.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexDocumentsUseCaseTest {

    private lateinit var fileReader: DocumentFileScanner
    private lateinit var fixedStrategy: ChunkingStrategy
    private lateinit var embeddingService: EmbeddingService
    private lateinit var indexRepository: DocumentIndexRepository
    private lateinit var useCase: IndexDocumentsUseCase

    @BeforeEach
    fun setUp() {
        fileReader = mockk()
        fixedStrategy = mockk()
        embeddingService = mockk()
        indexRepository = mockk()

        every { fixedStrategy.type } returns ChunkingStrategyType.FIXED_SIZE

        useCase = IndexDocumentsUseCase(
            fileReader = fileReader,
            strategies = mapOf(ChunkingStrategyType.FIXED_SIZE to fixedStrategy),
            embeddingService = embeddingService,
            indexRepository = indexRepository
        )
    }

    @Test
    fun execute_fullPipeline_scanChunkEmbedSave() = runTest {
        // Arrange
        val docs = listOf(
            ScannedDocument("/path/a.txt", "a.txt", "content A", ".txt")
        )
        val chunks = listOf(
            DocumentChunk(
                chunkId = "c1",
                content = "content A",
                metadata = ChunkMetadata("/path/a.txt", "a.txt", "", 0, 0, 2)
            )
        )
        val embedding = listOf(0.1f, 0.2f, 0.3f)

        every { fileReader.readDocuments(any()) } returns docs
        every { fixedStrategy.chunk("content A", "/path/a.txt", "a.txt") } returns chunks
        coEvery { embeddingService.embed(listOf("content A")) } returns listOf(embedding)
        coEvery { indexRepository.save(any(), any()) } just Runs

        val progressStates = mutableListOf<IndexingProgress>()

        // Act
        val result = useCase.execute("/test/dir", ChunkingStrategyType.FIXED_SIZE) {
            progressStates.add(it)
        }

        // Assert
        assertEquals(1, result.totalChunks)
        assertTrue(result.errors.isEmpty())
        coVerify { indexRepository.save(any(), any()) }

        // Verify progress callback was invoked with expected states
        val stateSequence = progressStates.map { it.state }
        assertTrue(stateSequence.contains(IndexingState.SCANNING))
        assertTrue(stateSequence.contains(IndexingState.CHUNKING))
        assertTrue(stateSequence.contains(IndexingState.EMBEDDING))
        assertTrue(stateSequence.contains(IndexingState.SAVING))
        assertTrue(stateSequence.contains(IndexingState.DONE))
    }

    @Test
    fun execute_progressCallbackInvokedOnEachStage() = runTest {
        // Arrange
        every { fileReader.readDocuments(any()) } returns emptyList()
        coEvery { indexRepository.save(any(), any()) } just Runs

        val progressStates = mutableListOf<IndexingState>()

        // Act
        useCase.execute("/empty/dir", ChunkingStrategyType.FIXED_SIZE) {
            progressStates.add(it.state)
        }

        // Assert
        assertTrue(progressStates.contains(IndexingState.SCANNING))
        assertTrue(progressStates.contains(IndexingState.CHUNKING))
        assertTrue(progressStates.contains(IndexingState.EMBEDDING))
        assertTrue(progressStates.contains(IndexingState.SAVING))
        assertTrue(progressStates.contains(IndexingState.DONE))
    }

    @Test
    fun execute_embeddingFailure_producesPartialResultWithErrors() = runTest {
        // Arrange
        val docs = listOf(
            ScannedDocument("/path/a.txt", "a.txt", "content A", ".txt"),
            ScannedDocument("/path/b.txt", "b.txt", "content B", ".txt")
        )
        val chunksA = listOf(
            DocumentChunk("c1", "content A", ChunkMetadata("/path/a.txt", "a.txt", "", 0, 0, 2))
        )
        val chunksB = listOf(
            DocumentChunk("c2", "content B", ChunkMetadata("/path/b.txt", "b.txt", "", 0, 0, 2))
        )

        every { fileReader.readDocuments(any()) } returns docs
        every { fixedStrategy.chunk("content A", "/path/a.txt", "a.txt") } returns chunksA
        every { fixedStrategy.chunk("content B", "/path/b.txt", "b.txt") } returns chunksB

        // Both chunks go into a single batch of <=20, embedding fails
        coEvery { embeddingService.embed(listOf("content A", "content B")) } throws RuntimeException("API error")
        coEvery { indexRepository.save(any(), any()) } just Runs

        val progressStates = mutableListOf<IndexingProgress>()

        // Act
        val result = useCase.execute("/test/dir", ChunkingStrategyType.FIXED_SIZE) {
            progressStates.add(it)
        }

        // Assert
        assertEquals(0, result.totalChunks, "No chunks should be embedded when embedding fails")
        assertTrue(result.errors.isNotEmpty(), "Errors list should contain embedding failures")
        assertTrue(result.errors.any { it.message.contains("Embedding failed") })
    }

    @Test
    fun execute_unknownStrategy_throwsIllegalArgumentException() = runTest {
        // Arrange - useCase only has FIXED_SIZE strategy

        // Act & Assert
        try {
            useCase.execute("/dir", ChunkingStrategyType.STRUCTURAL) {}
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Unknown chunking strategy"))
        }
    }

    @Test
    fun execute_chunkingFailure_recordsErrorAndContinues() = runTest {
        // Arrange
        val docs = listOf(
            ScannedDocument("/path/bad.txt", "bad.txt", "bad content", ".txt"),
            ScannedDocument("/path/good.txt", "good.txt", "good content", ".txt")
        )
        val goodChunks = listOf(
            DocumentChunk("c1", "good content", ChunkMetadata("/path/good.txt", "good.txt", "", 0, 0, 3))
        )

        every { fileReader.readDocuments(any()) } returns docs
        every { fixedStrategy.chunk("bad content", "/path/bad.txt", "bad.txt") } throws RuntimeException("parse error")
        every { fixedStrategy.chunk("good content", "/path/good.txt", "good.txt") } returns goodChunks
        coEvery { embeddingService.embed(listOf("good content")) } returns listOf(listOf(0.1f, 0.2f))
        coEvery { indexRepository.save(any(), any()) } just Runs

        // Act
        val result = useCase.execute("/test/dir", ChunkingStrategyType.FIXED_SIZE) {}

        // Assert
        assertEquals(1, result.totalChunks, "Good chunk should be embedded")
        assertTrue(result.errors.any { it.file == "/path/bad.txt" })
    }

    @Test
    fun execute_noDocuments_savesEmptyIndex() = runTest {
        // Arrange
        every { fileReader.readDocuments(any()) } returns emptyList()
        coEvery { indexRepository.save(any(), any()) } just Runs

        // Act
        val result = useCase.execute("/empty", ChunkingStrategyType.FIXED_SIZE) {}

        // Assert
        assertEquals(0, result.totalChunks)
        assertTrue(result.errors.isEmpty())
        coVerify { indexRepository.save(any(), match { it.chunks.isEmpty() }) }
    }

    @Test
    fun computeProjectHash_sameDirectory_sameHash() {
        // Act
        val hash1 = IndexDocumentsUseCase.computeProjectHash("/some/path")
        val hash2 = IndexDocumentsUseCase.computeProjectHash("/some/path")

        // Assert
        assertEquals(hash1, hash2)
    }

    @Test
    fun computeProjectHash_differentDirectories_differentHashes() {
        // Act
        val hash1 = IndexDocumentsUseCase.computeProjectHash("/path/a")
        val hash2 = IndexDocumentsUseCase.computeProjectHash("/path/b")

        // Assert
        assertTrue(hash1 != hash2)
    }

    @Test
    fun computeProjectHash_returnsTwelveCharacterString() {
        // Act
        val hash = IndexDocumentsUseCase.computeProjectHash("/any/path")

        // Assert
        assertEquals(12, hash.length)
    }
}
