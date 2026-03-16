package org.bothubclient.infrastructure.docindex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bothubclient.config.DocumentIndexConfig
import org.bothubclient.domain.docindex.DocumentIndexRepository
import org.bothubclient.domain.docindex.StoredDocumentIndex
import org.bothubclient.infrastructure.logging.AppLogger
import org.bothubclient.infrastructure.persistence.AtomicFileWriter
import org.bothubclient.infrastructure.persistence.dto.DocumentIndexFileDto
import org.bothubclient.infrastructure.persistence.dto.toDomain
import org.bothubclient.infrastructure.persistence.dto.toDto
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText

class FileDocumentIndexRepository(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    },
    baseDir: Path = Path.of(System.getProperty("user.home"), ".bothubclient", DocumentIndexConfig.INDEX_BASE_DIR)
) : DocumentIndexRepository {

    private val tag = "FileDocumentIndexRepository"
    private val mutex = Mutex()
    private val indexBaseDir: Path = runCatching {
        baseDir.apply { createDirectories() }
    }.getOrElse {
        Path.of(System.getProperty("user.dir"), ".bothubclient", DocumentIndexConfig.INDEX_BASE_DIR)
            .apply { createDirectories() }
    }
    private val createdDirs = mutableSetOf<String>()

    private fun indexFile(projectHash: String): Path {
        require(projectHash.matches(Regex("^[a-f0-9]{1,64}$"))) {
            "Invalid project hash: must be lowercase hex"
        }
        val dir = indexBaseDir.resolve(projectHash).toAbsolutePath().normalize()
        require(dir.startsWith(indexBaseDir.toAbsolutePath().normalize())) {
            "Path traversal detected for hash: $projectHash"
        }
        if (createdDirs.add(projectHash)) {
            dir.createDirectories()
        }
        return dir.resolve("index.json")
    }

    override suspend fun save(projectHash: String, index: StoredDocumentIndex) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val dto = index.toDto()
            val jsonStr = json.encodeToString(dto)
            val file = indexFile(projectHash)
            AtomicFileWriter.write(file = file, tempPrefix = "doc_index_", content = jsonStr)
            AppLogger.i(tag, "Saved index for project $projectHash (${index.chunks.size} chunks)")
        }
    }

    override suspend fun load(projectHash: String): StoredDocumentIndex? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = indexFile(projectHash)
            if (!file.exists()) return@withContext null
            val content = runCatching { file.readText() }.getOrElse { e ->
                AppLogger.e(tag, "Failed to read index file for $projectHash", e)
                return@withContext null
            }
            if (content.isBlank()) return@withContext null
            runCatching {
                json.decodeFromString<DocumentIndexFileDto>(content).toDomain()
            }.getOrElse { e ->
                AppLogger.e(tag, "Failed to decode index for $projectHash", e)
                null
            }
        }
    }

    override suspend fun delete(projectHash: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val dir = indexBaseDir.resolve(projectHash).toAbsolutePath().normalize()
            if (dir.startsWith(indexBaseDir.toAbsolutePath().normalize()) && dir.exists()) {
                runCatching { dir.toFile().deleteRecursively() }
            }
            createdDirs.remove(projectHash)
            AppLogger.i(tag, "Deleted index for project $projectHash")
        }
    }

    override suspend fun exists(projectHash: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            indexFile(projectHash).exists()
        }
    }
}
