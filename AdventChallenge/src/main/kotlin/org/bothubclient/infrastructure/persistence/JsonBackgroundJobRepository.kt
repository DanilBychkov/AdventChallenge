package org.bothubclient.infrastructure.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bothubclient.domain.entity.BackgroundJob
import org.bothubclient.domain.repository.BackgroundJobRepository
import org.bothubclient.infrastructure.logging.AppLogger
import org.bothubclient.infrastructure.persistence.dto.BackgroundJobsFileDto
import org.bothubclient.infrastructure.persistence.dto.toDomain
import org.bothubclient.infrastructure.persistence.dto.toDto
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText

class JsonBackgroundJobRepository(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    },
    baseDir: Path = Path.of(System.getProperty("user.home"), ".bothubclient")
) : BackgroundJobRepository {

    private val tag = "JsonBackgroundJobRepository"
    private val mutex = Mutex()

    private val file: Path = runCatching {
        baseDir.apply { createDirectories() }.resolve("background_jobs.json")
    }.getOrElse {
        Path.of(System.getProperty("user.dir"), ".bothubclient")
            .apply { createDirectories() }
            .resolve("background_jobs.json")
    }

    override suspend fun loadAll(): List<BackgroundJob> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            val content = runCatching { file.readText() }.getOrElse { e ->
                AppLogger.e(tag, "Failed to read jobs file", e)
                return@withContext emptyList()
            }
            if (content.isBlank()) return@withContext emptyList()
            runCatching {
                json.decodeFromString<BackgroundJobsFileDto>(content).jobs.map { it.toDomain() }
            }.getOrElse { e ->
                AppLogger.e(tag, "Failed to decode jobs", e)
                emptyList()
            }
        }
    }

    override suspend fun save(job: BackgroundJob) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = loadAllInternal()
            val updated = current.filterNot { it.id == job.id } + job
            writeAll(updated)
        }
    }

    override suspend fun findById(id: String): BackgroundJob? = mutex.withLock {
        withContext(Dispatchers.IO) {
            loadAllInternal().firstOrNull { it.id == id }
        }
    }

    override suspend fun delete(id: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = loadAllInternal()
            writeAll(current.filterNot { it.id == id })
        }
    }

    private fun loadAllInternal(): List<BackgroundJob> {
        if (!file.exists()) return emptyList()
        val content = runCatching { file.readText() }.getOrElse { return emptyList() }
        if (content.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<BackgroundJobsFileDto>(content).jobs.map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    private suspend fun writeAll(jobs: List<BackgroundJob>) {
        val dto = BackgroundJobsFileDto(jobs = jobs.map { it.toDto() })
        val jsonStr = json.encodeToString(dto)
        AtomicFileWriter.write(file = file, tempPrefix = "bg_jobs_", content = jsonStr)
    }
}
