package org.bothubclient.domain.docindex

data class ScannedDocument(
    val path: String,
    val fileName: String,
    val content: String,
    val extension: String
)

interface DocumentFileScanner {
    fun readDocuments(directoryPath: String): List<ScannedDocument>
}
