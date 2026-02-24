package org.bothubclient.infrastructure.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.repository.ChatHistoryStorage
import org.bothubclient.infrastructure.persistence.dto.ChatHistoryDto
import org.bothubclient.infrastructure.persistence.dto.toDomain
import org.bothubclient.infrastructure.persistence.dto.toDto
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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

    private val baseDir: Path = baseDir.apply { createDirectories() }

    private val sessionMutexes = mutableMapOf<String, Mutex>()
    private val mutexMapLock = Any()

    private fun getMutex(sessionId: String): Mutex = synchronized(mutexMapLock) {
        sessionMutexes.getOrPut(sessionId) { Mutex() }
    }

    override suspend fun loadHistory(sessionId: String): List<Message> {
        val file = getHistoryFile(sessionId)
        return getMutex(sessionId).withLock {
            if (!file.exists()) {
                println("[ChatHistory] File not found for session: $sessionId")
                return emptyList()
            }

            runCatching {
                val content = file.readText()
                if (content.isBlank()) {
                    println("[ChatHistory] File is empty for session: $sessionId")
                    return emptyList()
                }

                val dto = json.decodeFromString<ChatHistoryDto>(content)
                println("[ChatHistory] Loaded ${dto.messages.size} messages for session: $sessionId")
                dto.messages.map { it.toDomain() }
            }.getOrElse { e ->
                println("[ChatHistory] Failed to load history for $sessionId: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }

    override suspend fun saveHistory(sessionId: String, messages: List<Message>) {
        val file = getHistoryFile(sessionId)
        println("[ChatHistory] saveHistory called for session: $sessionId, messages: ${messages.size}, file: $file")
        getMutex(sessionId).withLock {
            try {
                atomicWrite(file, sessionId, messages)
                println("[ChatHistory] saveHistory SUCCESS for session: $sessionId")
            } catch (e: Exception) {
                println("[ChatHistory] saveHistory FAILED for session: $sessionId")
                println("[ChatHistory] Error type: ${e.javaClass.name}")
                println("[ChatHistory] Error message: ${e.message}")
                e.printStackTrace()
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

    private fun atomicWrite(file: Path, sessionId: String, messages: List<Message>) {
        println("[ChatHistory] atomicWrite START for file: $file")
        val existingCreatedAt = loadCreatedAt(file)
        val now = Instant.now().toString()

        val dto = ChatHistoryDto(
            version = 1,
            sessionId = sessionId,
            messages = messages.map { it.toDto() },
            createdAt = existingCreatedAt ?: now,
            updatedAt = now
        )

        println("[ChatHistory] DTO created, encoding to JSON...")
        val jsonStr = json.encodeToString(dto)
        println("[ChatHistory] JSON length: ${jsonStr.length}")

        var lastException: Exception? = null
        repeat(3) { attempt ->
            var tempFile: Path? = null
            try {
                tempFile = Files.createTempFile("chat_history_", ".tmp")
                println("[ChatHistory] Created temp file: $tempFile (attempt ${attempt + 1})")

                Files.writeString(tempFile, jsonStr)

                java.io.FileOutputStream(tempFile.toFile(), true).use { fos ->
                    fos.channel.force(true)
                }

                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING)
                println("[ChatHistory] atomicWrite SUCCESS for file: $file")
                return
            } catch (e: java.nio.file.AccessDeniedException) {
                println("[ChatHistory] AccessDenied on attempt ${attempt + 1}: ${e.message}")
                lastException = e
                tempFile?.let {
                    try {
                        Files.deleteIfExists(it)
                    } catch (_: Exception) {
                    }
                }
                Thread.sleep(150L * (attempt + 1))
            } catch (e: Exception) {
                println("[ChatHistory] atomicWrite FAILED: ${e.javaClass.name} - ${e.message}")
                tempFile?.let {
                    try {
                        Files.deleteIfExists(it)
                    } catch (_: Exception) {
                    }
                }
                throw e
            }
        }
        throw lastException ?: IllegalStateException("Failed to write file after 3 attempts")
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
