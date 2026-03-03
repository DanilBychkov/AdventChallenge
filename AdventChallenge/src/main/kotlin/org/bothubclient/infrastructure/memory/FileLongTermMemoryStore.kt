package org.bothubclient.infrastructure.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bothubclient.domain.entity.FactEntry
import org.bothubclient.domain.entity.WmCategory
import org.bothubclient.domain.memory.LongTermMemoryStore
import org.bothubclient.domain.memory.MemoryItem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class FileLongTermMemoryStore(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    },
    baseDir: Path = Path.of(System.getProperty("user.home"), ".bothubclient", "ltm")
) : LongTermMemoryStore {

    @Serializable
    private data class LtmDto(
        val version: Int = 1,
        val items: List<LtmItemDto> = emptyList()
    )

    @Serializable
    private data class LtmItemDto(
        val category: String,
        val key: String,
        val value: String,
        val confidence: Float = 1.0f,
        val timestamp: Long,
        val source: String = "",
        val useCount: Int = 0,
        val lastUsed: Long = timestamp,
        val ttl: Long? = null
    )

    private val baseDir: Path =
        runCatching { baseDir.apply { createDirectories() } }
            .getOrElse {
                Path.of(System.getProperty("user.dir"), ".bothubclient", "ltm").apply {
                    createDirectories()
                }
            }
    private val file: Path = baseDir.resolve("ltm.json")

    private val mutex = Mutex()
    private var loaded = false
    private val cache = LinkedHashMap<String, LtmItemDto>()

    private fun compositeKey(category: String, key: String): String = "${category}::$key"

    private suspend fun ensureLoaded() {
        if (loaded) return
        if (!file.exists()) {
            loaded = true
            return
        }
        val content = withContext(Dispatchers.IO) { file.readText() }
        if (content.isBlank()) {
            loaded = true
            return
        }
        val dto = runCatching { json.decodeFromString(LtmDto.serializer(), content) }.getOrNull()
        dto?.items?.forEach { item ->
            cache[compositeKey(item.category, item.key)] = item
        }
        loaded = true
    }

    private suspend fun persistNow() {
        val dto = LtmDto(items = cache.values.toList())
        val content = json.encodeToString(dto)
        withContext(Dispatchers.IO) { atomicWrite(file, content) }
    }

    override suspend fun upsert(item: MemoryItem): Boolean {
        return mutex.withLock {
            ensureLoaded()
            val now = System.currentTimeMillis()
            val category = item.category.name
            val key = item.key.trim()
            val value = item.entry.value.trim()
            if (key.isBlank() || value.isBlank()) return@withLock false

            val id = compositeKey(category, key)
            val prev = cache[id]

            val timestamp =
                if (prev != null && prev.value == value) {
                    prev.timestamp
                } else {
                    now
                }

            val useCount = prev?.useCount ?: 0
            val lastUsed = prev?.lastUsed ?: now

            cache[id] =
                LtmItemDto(
                    category = category,
                    key = key,
                    value = value,
                    confidence = item.entry.confidence,
                    timestamp = timestamp,
                    source = item.entry.source,
                    useCount = useCount,
                    lastUsed = lastUsed,
                    ttl = item.entry.ttl
                )

            persistNow()
            true
        }
    }

    override suspend fun search(query: String, limit: Int): List<MemoryItem> {
        return mutex.withLock {
            ensureLoaded()
            val q = query.trim()
            if (q.isBlank() || cache.isEmpty()) return@withLock emptyList()

            val now = System.currentTimeMillis()
            val tokens =
                q.lowercase()
                    .split(Regex("""\s+"""))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(12)

            data class Scored(val dto: LtmItemDto, val score: Double)

            fun score(dto: LtmItemDto): Double {
                val hay = (dto.key + " " + dto.value).lowercase()
                val hits = tokens.sumOf { t -> if (hay.contains(t)) 1 else 0 }
                if (hits == 0) return 0.0
                val recencyDays = (now - dto.lastUsed).coerceAtLeast(0) / (1000.0 * 60 * 60 * 24)
                val recencyBoost = 1.0 / (1.0 + recencyDays)
                return hits * 5.0 + dto.confidence.toDouble() * 2.0 + recencyBoost
            }

            val scored =
                cache.values
                    .map { Scored(it, score(it)) }
                    .filter { it.score > 0.0 }
                    .sortedByDescending { it.score }
                    .take(limit.coerceIn(1, 50))

            if (scored.isEmpty()) return@withLock emptyList()

            val idsToTouch = mutableListOf<String>()
            scored.forEach { s ->
                idsToTouch += compositeKey(s.dto.category, s.dto.key)
            }
            idsToTouch.forEach { id ->
                val dto = cache[id] ?: return@forEach
                cache[id] = dto.copy(useCount = dto.useCount + 1, lastUsed = now)
            }
            persistNow()

            scored.map { s ->
                MemoryItem(
                    category = runCatching { WmCategory.valueOf(s.dto.category) }.getOrElse { WmCategory.CONTEXT },
                    key = s.dto.key,
                    entry =
                        FactEntry(
                            value = s.dto.value,
                            confidence = s.dto.confidence,
                            timestamp = s.dto.timestamp,
                            source = s.dto.source,
                            useCount = s.dto.useCount + 1,
                            lastUsed = now,
                            ttl = s.dto.ttl
                        )
                )
            }
        }
    }

    override suspend fun snapshot(): List<MemoryItem> {
        return mutex.withLock {
            ensureLoaded()
            cache.values
                .sortedByDescending { it.lastUsed }
                .map { dto ->
                    MemoryItem(
                        category = runCatching { WmCategory.valueOf(dto.category) }.getOrElse { WmCategory.CONTEXT },
                        key = dto.key,
                        entry =
                            FactEntry(
                                value = dto.value,
                                confidence = dto.confidence,
                                timestamp = dto.timestamp,
                                source = dto.source,
                                useCount = dto.useCount,
                                lastUsed = dto.lastUsed,
                                ttl = dto.ttl
                            )
                    )
                }
        }
    }

    override suspend fun deleteWhere(criteria: (MemoryItem) -> Boolean): Int {
        return mutex.withLock {
            ensureLoaded()
            val toRemove =
                cache.values.filter { dto ->
                    val item =
                        MemoryItem(
                            category = runCatching { WmCategory.valueOf(dto.category) }.getOrElse { WmCategory.CONTEXT },
                            key = dto.key,
                            entry =
                                FactEntry(
                                    value = dto.value,
                                    confidence = dto.confidence,
                                    timestamp = dto.timestamp,
                                    source = dto.source,
                                    useCount = dto.useCount,
                                    lastUsed = dto.lastUsed,
                                    ttl = dto.ttl
                                )
                        )
                    criteria(item)
                }
            if (toRemove.isEmpty()) return@withLock 0
            toRemove.forEach { dto -> cache.remove(compositeKey(dto.category, dto.key)) }
            persistNow()
            toRemove.size
        }
    }

    private fun atomicWrite(file: Path, content: String) {
        var lastException: Exception? = null
        repeat(3) { attempt ->
            var tempFile: Path? = null
            try {
                tempFile = Files.createTempFile("ltm_", ".tmp")
                tempFile.writeText(content)
                java.io.FileOutputStream(tempFile.toFile(), true).use { fos -> fos.channel.force(true) }
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING)
                return
            } catch (e: java.nio.file.AccessDeniedException) {
                lastException = e
                tempFile?.let { Files.deleteIfExists(it) }
                Thread.sleep(150L * (attempt + 1))
            } catch (e: Exception) {
                tempFile?.let { Files.deleteIfExists(it) }
                throw e
            }
        }
        throw lastException ?: IllegalStateException("Failed to write file after 3 attempts")
    }
}
