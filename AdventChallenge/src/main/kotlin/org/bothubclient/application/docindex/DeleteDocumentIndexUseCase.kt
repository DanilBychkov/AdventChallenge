package org.bothubclient.application.docindex

import org.bothubclient.domain.docindex.DocumentIndexRepository

class DeleteDocumentIndexUseCase(
    private val indexRepository: DocumentIndexRepository
) {

    suspend fun execute(projectHash: String) {
        indexRepository.delete(projectHash)
    }
}
