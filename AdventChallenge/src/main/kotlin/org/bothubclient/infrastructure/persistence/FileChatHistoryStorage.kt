package org.bothubclient.infrastructure.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.repository.ChatHistoryStorage
import org.bothubclient.infrastructure.logging.AppLogger
import org.bothubclient.infrastructure.persistence.dto.ChatHistoryDto
import org.bothubclient.infrastructure.persistence.dto.toDomain
import org.bothubclient.infrastructure.persistence.dto.toDto
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.*

class FileChatHistoryStorage(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    },
    baseDir: Path = Path.of(System.getProperty("user.home"), ".bothubclient", "chat_history")
) : ChatHistoryStorage {
    private val tag = "FileChatHistoryStorage"

    private val baseDir: Path =
        runCatching { baseDir.apply { createDirectories() } }
            .getOrElse {
                Path.of(System.getProperty("user.dir"), ".bothubclient", "chat_history").apply {
                    createDirectories()
                }
            }

    private val sessionMutexes = mutableMapOf<String, Mutex>()
    private val mutexMapLock = Any()

    private fun getMutex(sessionId: String): Mutex = synchronized(mutexMapLock) {
        sessionMutexes.getOrPut(sessionId) { Mutex() }
    }

    override suspend fun loadHistory(sessionId: String): List<Message> {
        val file = getHistoryFile(sessionId)
        return getMutex(sessionId).withLock {
            withContext(Dispatchers.IO) {
                if (!file.exists()) {
                    return@withContext emptyList()
                }

                val content =
                    runCatching { file.readText() }.getOrElse { e ->
                        AppLogger.e(tag, "Failed to read history for sessionId=$sessionId", e)
                        return@withContext emptyList()
                    }
                if (content.isBlank()) {
                    return@withContext emptyList()
                }

                runCatching {
                    val dto = json.decodeFromString<ChatHistoryDto>(content)
                    dto.messages.map { it.toDomain() }
                }.getOrElse { e ->
                    AppLogger.e(tag, "Failed to decode history for sessionId=$sessionId", e)
                    emptyList()
                }
            }
        }
    }

    override suspend fun saveHistory(sessionId: String, messages: List<Message>) {
        val file = getHistoryFile(sessionId)
        getMutex(sessionId).withLock {
            try {
                withContext(Dispatchers.IO) { atomicWrite(file, sessionId, messages) }
            } catch (e: Exception) {
                AppLogger.e(tag, "saveHistory FAILED for sessionId=$sessionId", e)
                throw e
            }
        }
    }

    override suspend fun deleteHistory(sessionId: String) {
        getMutex(sessionId).withLock {
            val file = getHistoryFile(sessionId)
            if (file.exists()) {
                Files.delete(file)
            }
        }
    }

    override suspend fun listSessions(): List<String> = withContext(Dispatchers.IO) {
        Files.list(baseDir)
            .filter { it.extension == "json" }
            .map { it.nameWithoutExtension }
            .toList()
    }

    private suspend fun atomicWrite(file: Path, sessionId: String, messages: List<Message>) {
        val existingCreatedAt = loadCreatedAt(file)
        val now = Instant.now().toString()

        val dto = ChatHistoryDto(
            version = 1,
            sessionId = sessionId,
            messages = messages.map { it.toDto() },
            createdAt = existingCreatedAt ?: now,
            updatedAt = now
        )

        val jsonStr = json.encodeToString(dto)
        AtomicFileWriter.write(
            file = file,
            tempPrefix = "chat_history_",
            content = jsonStr
        )
    }

    private fun loadCreatedAt(file: Path): String? {
        if (!file.exists()) return null
        return runCatching {
            val dto = json.decodeFromString<ChatHistoryDto>(file.readText())
            dto.createdAt
        }.getOrNull()
    }

    private fun getHistoryFile(sessionId: String): Path {
        val safeName = sessionId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return baseDir.resolve("$safeName.json")
    }
}
