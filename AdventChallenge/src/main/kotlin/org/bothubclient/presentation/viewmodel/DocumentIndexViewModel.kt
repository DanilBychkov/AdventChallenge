package org.bothubclient.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.bothubclient.application.docindex.DeleteDocumentIndexUseCase
import org.bothubclient.application.docindex.IndexDocumentsUseCase
import org.bothubclient.application.docindex.SearchDocumentsUseCase
import org.bothubclient.domain.docindex.ChunkingStrategyType
import org.bothubclient.domain.docindex.IndexingProgress
import org.bothubclient.domain.docindex.IndexingState
import org.bothubclient.infrastructure.logging.AppLogger
import org.bothubclient.infrastructure.persistence.DocumentIndexPreferences
import org.bothubclient.infrastructure.persistence.DocumentIndexPreferencesStorage

data class IndexStats(
    val chunkCount: Int = 0,
    val fileCount: Int = 0,
    val createdDate: String = ""
)

data class ComparisonResult(
    val strategyName: String,
    val chunkCount: Int,
    val avgTokens: Int,
    val avgSimilarity: Float,
    val timeMs: Long
)

class DocumentIndexViewModel(
    private val indexDocumentsUseCase: IndexDocumentsUseCase,
    private val searchDocumentsUseCase: SearchDocumentsUseCase,
    private val deleteDocumentIndexUseCase: DeleteDocumentIndexUseCase,
    private val preferencesStorage: DocumentIndexPreferencesStorage = DocumentIndexPreferencesStorage(),
    private val onProjectHashChanged: (String?) -> Unit = {},
    private val onIndexEnabledChanged: (Boolean) -> Unit = {}
) {

    private val tag = "DocumentIndexViewModel"

    var isDialogOpen by mutableStateOf(false)
        private set

    var selectedDirectory by mutableStateOf("")
        private set

    var selectedStrategy by mutableStateOf(ChunkingStrategyType.FIXED_SIZE)
        private set

    var indexingProgress by mutableStateOf(IndexingProgress())
        private set

    var isIndexReady by mutableStateOf(false)
        private set

    var isIndexEnabled by mutableStateOf(false)
        private set

    var indexStats by mutableStateOf(IndexStats())
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var projectHash by mutableStateOf<String?>(null)
        private set

    var comparisonResults by mutableStateOf<List<ComparisonResult>>(emptyList())
        private set

    var isComparing by mutableStateOf(false)
        private set

    var isComparisonDialogOpen by mutableStateOf(false)
        private set

    private var indexingJob: Job? = null

    init {
        val prefs = preferencesStorage.load()
        if (prefs.projectHash.isNotBlank() && prefs.directory.isNotBlank()) {
            selectedDirectory = prefs.directory
            projectHash = prefs.projectHash
            isIndexReady = true
            isIndexEnabled = prefs.enabled
            if (prefs.enabled) {
                onProjectHashChanged(prefs.projectHash)
                onIndexEnabledChanged(true)
            }
            AppLogger.i(tag, "Restored index state: hash=${prefs.projectHash}, enabled=${prefs.enabled}")
        }
    }

    fun openDialog() {
        isDialogOpen = true
    }

    fun closeDialog() {
        isDialogOpen = false
    }

    fun setDirectory(path: String) {
        selectedDirectory = path
        errorMessage = null
    }

    fun setStrategy(strategy: ChunkingStrategyType) {
        selectedStrategy = strategy
    }

    fun startIndexing(scope: CoroutineScope) {
        if (selectedDirectory.isBlank()) {
            errorMessage = "Please select a directory first"
            return
        }

        errorMessage = null
        indexingJob = scope.launch {
            try {
                val result = indexDocumentsUseCase.execute(
                    directory = selectedDirectory,
                    strategyType = selectedStrategy,
                    onProgress = { progress ->
                        indexingProgress = progress
                    }
                )

                projectHash = result.projectHash
                isIndexReady = true
                indexStats = IndexStats(
                    chunkCount = result.totalChunks,
                    fileCount = indexingProgress.totalFiles,
                    createdDate = java.time.Instant.now().toString()
                )
                onProjectHashChanged(result.projectHash)
                preferencesStorage.save(DocumentIndexPreferences(
                    directory = selectedDirectory,
                    projectHash = result.projectHash,
                    enabled = isIndexEnabled
                ))

                if (result.errors.isNotEmpty()) {
                    errorMessage = "${result.errors.size} errors during indexing"
                }
            } catch (e: Exception) {
                AppLogger.e(tag, "Indexing failed", e)
                errorMessage = "Indexing failed: ${e.message}"
                indexingProgress = indexingProgress.copy(state = IndexingState.ERROR)
            }
        }
    }

    fun cancelIndexing() {
        indexingJob?.cancel()
        indexingJob = null
        indexingProgress = IndexingProgress()
        errorMessage = "Indexing cancelled"
    }

    fun deleteIndex(scope: CoroutineScope) {
        val hash = projectHash ?: return
        scope.launch {
            try {
                deleteDocumentIndexUseCase.execute(hash)
                isIndexReady = false
                isIndexEnabled = false
                projectHash = null
                indexStats = IndexStats()
                indexingProgress = IndexingProgress()
                onProjectHashChanged(null)
                onIndexEnabledChanged(false)
                preferencesStorage.save(DocumentIndexPreferences())
            } catch (e: Exception) {
                AppLogger.e(tag, "Delete index failed", e)
                errorMessage = "Failed to delete index: ${e.message}"
            }
        }
    }

    fun toggleIndexEnabled() {
        isIndexEnabled = !isIndexEnabled
        onIndexEnabledChanged(isIndexEnabled)
        val hash = projectHash ?: return
        preferencesStorage.save(DocumentIndexPreferences(
            directory = selectedDirectory,
            projectHash = hash,
            enabled = isIndexEnabled
        ))
    }

    fun openComparisonDialog() {
        isComparisonDialogOpen = true
    }

    fun closeComparisonDialog() {
        isComparisonDialogOpen = false
    }

    fun startComparison(scope: CoroutineScope, testQueries: List<String>) {
        if (selectedDirectory.isBlank()) return
        if (testQueries.isEmpty()) return

        isComparing = true
        comparisonResults = emptyList()

        scope.launch {
            val tempHashes = mutableListOf<String>()
            try {
                val results = mutableListOf<ComparisonResult>()

                for (strategyType in ChunkingStrategyType.entries) {
                    val startMs = System.currentTimeMillis()
                    val tempHash = IndexDocumentsUseCase.computeProjectHash(
                        selectedDirectory + "::comparison::${strategyType.name}"
                    )
                    tempHashes.add(tempHash)

                    var totalChunksCharCount = 0L
                    var chunkCount = 0

                    val indexResult = indexDocumentsUseCase.execute(
                        directory = selectedDirectory,
                        strategyType = strategyType,
                        projectHashOverride = tempHash,
                        onProgress = { progress ->
                            chunkCount = progress.totalChunks
                        }
                    )

                    var totalSimilarity = 0f
                    var totalResults = 0

                    for (query in testQueries) {
                        val searchResults = searchDocumentsUseCase.execute(
                            query = query,
                            projectHash = tempHash
                        )
                        for (sr in searchResults) {
                            totalSimilarity += sr.similarity
                            totalResults++
                            totalChunksCharCount += sr.chunk.content.length
                        }
                    }

                    val timeMs = System.currentTimeMillis() - startMs
                    val avgSimilarity = if (totalResults > 0) totalSimilarity / totalResults else 0f
                    val avgTokens = if (totalResults > 0) {
                        (totalChunksCharCount / totalResults / 4).toInt()
                    } else 0

                    results.add(
                        ComparisonResult(
                            strategyName = strategyType.name,
                            chunkCount = indexResult.totalChunks,
                            avgTokens = avgTokens,
                            avgSimilarity = avgSimilarity,
                            timeMs = timeMs
                        )
                    )
                }

                comparisonResults = results
            } catch (e: Exception) {
                AppLogger.e(tag, "Comparison failed", e)
                errorMessage = "Comparison failed: ${e.message}"
            } finally {
                // Clean up temporary indices
                for (tempHash in tempHashes) {
                    runCatching { deleteDocumentIndexUseCase.execute(tempHash) }
                }
                isComparing = false
            }
        }
    }

    companion object {
        fun create(
            onProjectHashChanged: (String?) -> Unit = {},
            onIndexEnabledChanged: (Boolean) -> Unit = {}
        ): DocumentIndexViewModel {
            val sl = org.bothubclient.infrastructure.di.ServiceLocator
            return DocumentIndexViewModel(
                indexDocumentsUseCase = sl.indexDocumentsUseCase,
                searchDocumentsUseCase = sl.searchDocumentsUseCase,
                deleteDocumentIndexUseCase = sl.deleteDocumentIndexUseCase,
                preferencesStorage = sl.documentIndexPreferencesStorage,
                onProjectHashChanged = onProjectHashChanged,
                onIndexEnabledChanged = onIndexEnabledChanged
            )
        }
    }
}
