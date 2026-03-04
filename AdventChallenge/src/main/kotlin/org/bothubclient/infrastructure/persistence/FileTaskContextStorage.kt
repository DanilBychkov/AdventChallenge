package org.bothubclient.infrastructure.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bothubclient.domain.entity.TaskContext
import org.bothubclient.domain.repository.TaskContextStorage
import org.bothubclient.infrastructure.logging.FileLogger
import org.bothubclient.infrastructure.persistence.dto.TaskContextDto
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.*

class FileTaskContextStorage(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    },
    baseDir: Path = Path.of(System.getProperty("user.home"), ".bothubclient", "task_context")
) : TaskContextStorage {

    private val baseDir: Path =
        runCatching { baseDir.apply { createDirectories() } }
            .getOrElse {
                Path.of(System.getProperty("user.dir"), ".bothubclient", "task_context").apply {
                    createDirectories()
                }
            }

    private val mutexes = mutableMapOf<String, Mutex>()
    private val mutexMapLock = Any()

    private fun getMutex(key: String): Mutex = synchronized(mutexMapLock) {
        mutexes.getOrPut(key) { Mutex() }
    }

    override suspend fun load(sessionId: String, branchId: String): TaskContext? {
        val key = contextKey(sessionId, branchId)
        val file = getFile(sessionId, branchId)
        return getMutex(key).withLock {
            withContext(Dispatchers.IO) {
                if (!file.exists()) return@withContext null

                runCatching {
                    val content = file.readText()
                    if (content.isBlank()) return@runCatching null

                    val dto = json.decodeFromString<TaskContextDto>(content)
                    dto.context
                }.onFailure { e ->
                    FileLogger.log("FileTaskContextStorage", "Failed to load $key: ${e.message}")
                }.getOrNull()
            }
        }
    }

    override suspend fun save(sessionId: String, branchId: String, context: TaskContext?) {
        if (context == null) {
            delete(sessionId, branchId)
            return
        }

        val key = contextKey(sessionId, branchId)
        val file = getFile(sessionId, branchId)
        val dto = TaskContextDto(sessionId = sessionId, branchId = branchId, context = context)
        val jsonStr = json.encodeToString(dto)

        getMutex(key).withLock {
            withContext(Dispatchers.IO) {
                atomicWrite(file, jsonStr)
            }
        }
    }

    override suspend fun delete(sessionId: String, branchId: String) {
        val key = contextKey(sessionId, branchId)
        val file = getFile(sessionId, branchId)
        getMutex(key).withLock {
            withContext(Dispatchers.IO) {
                if (file.exists()) Files.delete(file)
            }
        }
    }

    suspend fun listSessions(): List<String> = withContext(Dispatchers.IO) {
        if (!baseDir.exists()) return@withContext emptyList()
        Files.list(baseDir)
            .filter { it.extension == "json" }
            .map { it.nameWithoutExtension }
            .toList()
    }

    private fun atomicWrite(file: Path, content: String) {
        var lastException: Exception? = null
        repeat(3) { attempt ->
            var tmp: Path? = null
            try {
                tmp = Files.createTempFile("task_context_", ".tmp")
                tmp.writeText(content)
                java.io.FileOutputStream(tmp.toFile(), true).use { fos ->
                    fos.channel.force(true)
                }
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
                return
            } catch (e: java.nio.file.AccessDeniedException) {
                lastException = e
                tmp?.let { runCatching { Files.deleteIfExists(it) } }
                Thread.sleep(150L * (attempt + 1))
            } catch (e: Exception) {
                tmp?.let { runCatching { Files.deleteIfExists(it) } }
                throw e
            }
        }
        throw lastException ?: IllegalStateException("Failed to write file after 3 attempts")
    }

    private fun contextKey(sessionId: String, branchId: String): String = "$sessionId::$branchId"

    private fun getFile(sessionId: String, branchId: String): Path {
        val safeSession = sessionId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val safeBranch = branchId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return baseDir.resolve("${safeSession}__${safeBranch}.json")
    }
}
