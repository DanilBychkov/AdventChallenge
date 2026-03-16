package org.bothubclient.infrastructure.docindex

import org.bothubclient.config.DocumentIndexConfig
import org.bothubclient.infrastructure.logging.AppLogger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.util.EnumSet
import org.bothubclient.domain.docindex.DocumentFileScanner
import org.bothubclient.domain.docindex.ScannedDocument
import java.nio.file.attribute.BasicFileAttributes

class DocumentFileReader : DocumentFileScanner {

    private val tag = "DocumentFileReader"

    override fun readDocuments(directoryPath: String): List<ScannedDocument> {
        val dir = Path.of(directoryPath).toRealPath()
        if (!Files.isDirectory(dir)) {
            AppLogger.w(tag, "Not a directory: $dir")
            return emptyList()
        }

        val results = mutableListOf<ScannedDocument>()
        val maxFiles = 10_000
        val maxDepth = 20

        Files.walkFileTree(dir, EnumSet.noneOf(java.nio.file.FileVisitOption::class.java), maxDepth, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (results.size >= maxFiles) {
                    AppLogger.w(tag, "Reached file limit ($maxFiles), stopping scan")
                    return FileVisitResult.TERMINATE
                }
                val realFile = file.toRealPath()

                if (!realFile.startsWith(dir)) {
                    AppLogger.w(tag, "Skipping file outside base directory (symlink?): $realFile")
                    return FileVisitResult.CONTINUE
                }

                val fileName = realFile.fileName.toString()
                val extension = fileName.substringAfterLast('.', "").let { ".$it" }

                if (extension !in DocumentIndexConfig.ALLOWED_EXTENSIONS) {
                    return FileVisitResult.CONTINUE
                }

                val size = attrs.size()
                if (size > DocumentIndexConfig.MAX_FILE_SIZE_BYTES) {
                    AppLogger.w(tag, "Skipping large file ($size bytes): $realFile")
                    return FileVisitResult.CONTINUE
                }

                val content = runCatching {
                    BufferedReader(InputStreamReader(Files.newInputStream(realFile), Charsets.UTF_8)).use {
                        it.readText()
                    }
                }.getOrElse { e ->
                    AppLogger.e(tag, "Failed to read file: $realFile", e)
                    return FileVisitResult.CONTINUE
                }

                results.add(
                    ScannedDocument(
                        path = realFile.toString(),
                        fileName = fileName,
                        content = content,
                        extension = extension
                    )
                )

                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult {
                AppLogger.w(tag, "Failed to visit file: $file (${exc.message})")
                return FileVisitResult.CONTINUE
            }
        })

        AppLogger.i(tag, "Read ${results.size} documents from $dir")
        return results
    }
}
