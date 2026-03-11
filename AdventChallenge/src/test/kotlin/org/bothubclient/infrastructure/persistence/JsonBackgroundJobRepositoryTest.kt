package org.bothubclient.infrastructure.persistence

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.entity.BackgroundJob
import org.bothubclient.domain.entity.JobStatus
import org.bothubclient.domain.entity.JobType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonBackgroundJobRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var repository: JsonBackgroundJobRepository

    @BeforeEach
    fun setUp() {
        repository = JsonBackgroundJobRepository(baseDir = tempDir)
    }

    private fun makeJob(
        id: String = "test-1",
        interval: Int = 5,
        enabled: Boolean = true,
        status: JobStatus = JobStatus.ACTIVE
    ) = BackgroundJob(
        id = id,
        type = JobType.BORED_REPORT,
        intervalMinutes = interval,
        enabled = enabled,
        status = status,
        nextRunEpochMs = System.currentTimeMillis() + interval * 60_000L,
        lastRunEpochMs = null,
        lastError = null,
        createdAtEpochMs = System.currentTimeMillis()
    )

    @Test
    fun `loadAll returns empty list when file does not exist`() = runTest {
        val result = repository.loadAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `save and loadAll round-trip`() = runTest {
        val job = makeJob()
        repository.save(job)
        val loaded = repository.loadAll()
        assertEquals(1, loaded.size)
        assertEquals(job.id, loaded[0].id)
        assertEquals(job.intervalMinutes, loaded[0].intervalMinutes)
        assertEquals(job.type, loaded[0].type)
        assertEquals(job.enabled, loaded[0].enabled)
    }

    @Test
    fun `save updates existing job`() = runTest {
        val job = makeJob()
        repository.save(job)
        val updated = job.copy(intervalMinutes = 15)
        repository.save(updated)
        val loaded = repository.loadAll()
        assertEquals(1, loaded.size)
        assertEquals(15, loaded[0].intervalMinutes)
    }

    @Test
    fun `findById returns correct job`() = runTest {
        repository.save(makeJob("a"))
        repository.save(makeJob("b"))
        val found = repository.findById("a")
        assertNotNull(found)
        assertEquals("a", found.id)
    }

    @Test
    fun `findById returns null for missing id`() = runTest {
        repository.save(makeJob("a"))
        assertNull(repository.findById("nonexistent"))
    }

    @Test
    fun `delete removes job`() = runTest {
        repository.save(makeJob("a"))
        repository.save(makeJob("b"))
        repository.delete("a")
        val loaded = repository.loadAll()
        assertEquals(1, loaded.size)
        assertEquals("b", loaded[0].id)
    }

    @Test
    fun `loadAll handles corrupted file gracefully`() = runTest {
        tempDir.resolve("background_jobs.json").writeText("NOT VALID JSON")
        val result = repository.loadAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `loadAll handles empty file gracefully`() = runTest {
        tempDir.resolve("background_jobs.json").writeText("")
        val result = repository.loadAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `concurrent writes preserve data integrity`() = runTest {
        val jobs = (1..10).map { makeJob("job-$it") }
        val deferred = jobs.map { job ->
            async { repository.save(job) }
        }
        deferred.awaitAll()
        val loaded = repository.loadAll()
        assertEquals(10, loaded.size)
        val ids = loaded.map { it.id }.toSet()
        jobs.forEach { assertTrue(it.id in ids) }
    }

    @Test
    fun `save preserves all fields`() = runTest {
        val job = BackgroundJob(
            id = "full-test",
            type = JobType.BORED_REPORT,
            intervalMinutes = 30,
            enabled = false,
            status = JobStatus.ERROR,
            nextRunEpochMs = 1700000000000L,
            lastRunEpochMs = 1699999000000L,
            lastError = "Connection timeout",
            createdAtEpochMs = 1699998000000L,
            retryCount = 3
        )
        repository.save(job)
        val loaded = repository.findById("full-test")
        assertNotNull(loaded)
        assertEquals(job.id, loaded.id)
        assertEquals(job.type, loaded.type)
        assertEquals(job.intervalMinutes, loaded.intervalMinutes)
        assertEquals(job.enabled, loaded.enabled)
        assertEquals(job.status, loaded.status)
        assertEquals(job.nextRunEpochMs, loaded.nextRunEpochMs)
        assertEquals(job.lastRunEpochMs, loaded.lastRunEpochMs)
        assertEquals(job.lastError, loaded.lastError)
        assertEquals(job.createdAtEpochMs, loaded.createdAtEpochMs)
        assertEquals(job.retryCount, loaded.retryCount)
    }
}
