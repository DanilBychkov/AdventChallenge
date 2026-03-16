package org.bothubclient.application.docindex

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.docindex.DocumentIndexRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteDocumentIndexUseCaseTest {

    private lateinit var indexRepository: DocumentIndexRepository
    private lateinit var useCase: DeleteDocumentIndexUseCase

    @BeforeEach
    fun setUp() {
        indexRepository = mockk()
        useCase = DeleteDocumentIndexUseCase(indexRepository)
    }

    @Test
    fun execute_delegatesToRepository() = runTest {
        // Arrange
        coEvery { indexRepository.delete("hash123") } just Runs

        // Act
        useCase.execute("hash123")

        // Assert
        coVerify(exactly = 1) { indexRepository.delete("hash123") }
    }

    @Test
    fun execute_passesExactProjectHash() = runTest {
        // Arrange
        coEvery { indexRepository.delete(any()) } just Runs

        // Act
        useCase.execute("specific-hash")

        // Assert
        coVerify { indexRepository.delete("specific-hash") }
    }

    @Test
    fun execute_repositoryThrows_propagatesException() = runTest {
        // Arrange
        coEvery { indexRepository.delete("bad") } throws RuntimeException("Delete failed")

        // Act & Assert
        try {
            useCase.execute("bad")
            throw AssertionError("Expected RuntimeException")
        } catch (e: RuntimeException) {
            kotlin.test.assertEquals("Delete failed", e.message)
        }
    }
}
