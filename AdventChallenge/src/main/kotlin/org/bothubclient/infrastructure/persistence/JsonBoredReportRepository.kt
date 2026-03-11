package org.bothubclient.infrastructure.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bothubclient.domain.entity.BoredReportItem
import org.bothubclient.domain.repository.BoredReportRepository
import org.bothubclient.infrastructure.logging.AppLogger
import org.bothubclient.infrastructure.persistence.dto.BoredReportsFileDto
import org.bothubclient.infrastructure.persistence.dto.toDomain
import org.bothubclient.infrastructure.persistence.dto.toDto
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText

class JsonBoredReportRepository(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    },
    baseDir: Path = Path.of(System.getProperty("user.home"), ".bothubclient")
) : BoredReportRepository {

    private val tag = "JsonBoredReportRepository"
    private val mutex = Mutex()

    private val file: Path = runCatching {
        baseDir.apply { createDirectories() }.resolve("bored_reports.json")
    }.getOrElse {
        Path.of(System.getProperty("user.dir"), ".bothubclient")
            .apply { createDirectories() }
            .resolve("bored_reports.json")
    }

    override suspend fun loadAll(): List<BoredReportItem> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            val content = runCatching { file.readText() }.getOrElse { e ->
                AppLogger.e(tag, "Failed to read reports file", e)
                return@withContext emptyList()
            }
            if (content.isBlank()) return@withContext emptyList()
            runCatching {
                json.decodeFromString<BoredReportsFileDto>(content).reports.map { it.toDomain() }
            }.getOrElse { e ->
                AppLogger.e(tag, "Failed to decode reports", e)
                emptyList()
            }
        }
    }

    override suspend fun add(report: BoredReportItem) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = loadAllInternal()
            writeAll(current + report)
        }
    }

    override suspend fun deleteByJobId(jobId: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = loadAllInternal()
            writeAll(current.filterNot { it.jobId == jobId })
        }
    }

    private fun loadAllInternal(): List<BoredReportItem> {
        if (!file.exists()) return emptyList()
        val content = runCatching { file.readText() }.getOrElse { return emptyList() }
        if (content.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<BoredReportsFileDto>(content).reports.map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    private suspend fun writeAll(reports: List<BoredReportItem>) {
        val dto = BoredReportsFileDto(reports = reports.map { it.toDto() })
        val jsonStr = json.encodeToString(dto)
        AtomicFileWriter.write(file = file, tempPrefix = "bored_reports_", content = jsonStr)
    }
}
