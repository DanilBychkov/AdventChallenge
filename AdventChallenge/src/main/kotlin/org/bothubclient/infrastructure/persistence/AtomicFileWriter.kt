package org.bothubclient.infrastructure.persistence

import kotlinx.coroutines.delay
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.writeText

object AtomicFileWriter {
    suspend fun write(
        file: Path,
        tempPrefix: String,
        content: String,
        attempts: Int = 3,
        baseDelayMs: Long = 150L
    ) {
        var lastException: Exception? = null
        repeat(attempts.coerceAtLeast(1)) { attempt ->
            var tempFile: Path? = null
            try {
                tempFile = Files.createTempFile(tempPrefix, ".tmp")
                tempFile.writeText(content)
                java.io.FileOutputStream(tempFile.toFile(), true).use { fos ->
                    fos.channel.force(true)
                }
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING)
                return
            } catch (e: java.nio.file.AccessDeniedException) {
                lastException = e
                tempFile?.let { Files.deleteIfExists(it) }
                delay(baseDelayMs * (attempt + 1))
            } catch (e: Exception) {
                tempFile?.let { Files.deleteIfExists(it) }
                throw e
            }
        }
        throw lastException ?: IllegalStateException("Failed to write file after $attempts attempts")
    }
}
