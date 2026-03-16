package org.bothubclient.application.docindex

import org.bothubclient.domain.docindex.*
import org.bothubclient.infrastructure.logging.AppLogger
import java.security.MessageDigest

data class IndexingResult(
    val projectHash: String,
    val totalChunks: Int,
    val errors: List<IndexingError>,
    val elapsedMs: Long
)

class IndexDocumentsUseCase(
    private val fileReader: DocumentFileScanner,
    private val strategies: Map<ChunkingStrategyType, ChunkingStrategy>,
    private val embeddingService: EmbeddingService,
    private val indexRepository: DocumentIndexRepository
) {

    private val tag = "IndexDocumentsUseCase"

    suspend fun execute(
        directory: String,
        strategyType: ChunkingStrategyType,
        projectHashOverride: String? = null,
        onProgress: (IndexingProgress) -> Unit
    ): IndexingResult {
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<IndexingError>()
        val projectHash = projectHashOverride ?: computeProjectHash(directory)
        val strategy = strategies[strategyType]
            ?: throw IllegalArgumentException("Unknown chunking strategy: $strategyType")

        AppLogger.i(tag, "Starting indexing: directory=$directory, strategy=$strategyType, hash=$projectHash")

        onProgress(IndexingProgress(state = IndexingState.SCANNING))

        val documents = fileReader.readDocuments(directory)
        val totalFiles = documents.size

        onProgress(
            IndexingProgress(
                state = IndexingState.CHUNKING,
                totalFiles = totalFiles,
                elapsedMs = System.currentTimeMillis() - startTime
            )
        )

        val allChunks = mutableListOf<DocumentChunk>()

        for ((index, doc) in documents.withIndex()) {
            val chunks = runCatching {
                strategy.chunk(content = doc.content, source = doc.path, title = doc.fileName)
            }.getOrElse { e ->
                errors.add(IndexingError(file = doc.path, message = "Chunking failed: ${e.message}"))
                AppLogger.e(tag, "Chunking failed for ${doc.path}", e)
                emptyList()
            }
            allChunks.addAll(chunks)

            onProgress(
                IndexingProgress(
                    state = IndexingState.CHUNKING,
                    totalFiles = totalFiles,
                    processedFiles = index + 1,
                    totalChunks = allChunks.size,
                    errors = errors.toList(),
                    elapsedMs = System.currentTimeMillis() - startTime
                )
            )
        }

        val totalChunks = allChunks.size
        AppLogger.i(tag, "Chunking complete: $totalChunks chunks from $totalFiles files")

        onProgress(
            IndexingProgress(
                state = IndexingState.EMBEDDING,
                totalFiles = totalFiles,
                processedFiles = totalFiles,
                totalChunks = totalChunks,
                elapsedMs = System.currentTimeMillis() - startTime
            )
        )

        val texts = allChunks.map { it.content }
        val embeddedChunks = mutableListOf<DocumentChunk>()
        var processedChunks = 0

        for (batch in texts.chunked(20)) {
            val batchEmbeddings = runCatching {
                embeddingService.embed(batch)
            }.getOrElse { e ->
                val batchStart = processedChunks
                for (i in batch.indices) {
                    val chunk = allChunks[batchStart + i]
                    errors.add(IndexingError(file = chunk.metadata.source, message = "Embedding failed: ${e.message}"))
                }
                AppLogger.e(tag, "Embedding batch failed at offset $processedChunks", e)
                processedChunks += batch.size
                continue
            }

            for (i in batch.indices) {
                val chunk = allChunks[processedChunks + i]
                embeddedChunks.add(chunk.copy(embedding = batchEmbeddings[i]))
            }

            processedChunks += batch.size

            onProgress(
                IndexingProgress(
                    state = IndexingState.EMBEDDING,
                    totalFiles = totalFiles,
                    processedFiles = totalFiles,
                    totalChunks = totalChunks,
                    processedChunks = processedChunks,
                    errors = errors.toList(),
                    elapsedMs = System.currentTimeMillis() - startTime
                )
            )
        }

        onProgress(
            IndexingProgress(
                state = IndexingState.SAVING,
                totalFiles = totalFiles,
                processedFiles = totalFiles,
                totalChunks = totalChunks,
                processedChunks = processedChunks,
                errors = errors.toList(),
                elapsedMs = System.currentTimeMillis() - startTime
            )
        )

        val storedIndex = StoredDocumentIndex(
            model = org.bothubclient.config.DocumentIndexConfig.EMBEDDING_MODEL,
            dimensions = org.bothubclient.config.DocumentIndexConfig.EMBEDDING_DIMENSIONS,
            strategy = strategyType.name,
            createdAt = java.time.Instant.now().toString(),
            sourceDirectory = directory,
            chunks = embeddedChunks
        )

        indexRepository.save(projectHash, storedIndex)

        val elapsedMs = System.currentTimeMillis() - startTime

        onProgress(
            IndexingProgress(
                state = IndexingState.DONE,
                totalFiles = totalFiles,
                processedFiles = totalFiles,
                totalChunks = embeddedChunks.size,
                processedChunks = embeddedChunks.size,
                errors = errors.toList(),
                elapsedMs = elapsedMs
            )
        )

        AppLogger.i(tag, "Indexing complete: ${embeddedChunks.size} chunks, ${errors.size} errors, ${elapsedMs}ms")

        return IndexingResult(
            projectHash = projectHash,
            totalChunks = embeddedChunks.size,
            errors = errors.toList(),
            elapsedMs = elapsedMs
        )
    }

    companion object {
        fun computeProjectHash(directory: String): String {
            val canonicalPath = java.nio.file.Path.of(directory).toAbsolutePath().normalize().toString()
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(canonicalPath.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }.take(12)
        }
    }
}
