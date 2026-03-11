package org.bothubclient.infrastructure.persistence

import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.entity.BoredReportItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonBoredReportRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var repository: JsonBoredReportRepository

    @BeforeEach
    fun setUp() {
        repository = JsonBoredReportRepository(baseDir = tempDir)
    }

    private fun makeReport(
        id: String = "r-1",
        jobId: String = "job-1",
        activity: String = "Test activity",
        summary: String = "Test summary"
    ) = BoredReportItem(
        id = id,
        jobId = jobId,
        activity = activity,
        llmSummary = summary,
        createdAtEpochMs = System.currentTimeMillis()
    )

    @Test
    fun `loadAll returns empty list when file does not exist`() = runTest {
        val result = repository.loadAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `add and loadAll round-trip`() = runTest {
        val report = makeReport()
        repository.add(report)
        val loaded = repository.loadAll()
        assertEquals(1, loaded.size)
        assertEquals(report.id, loaded[0].id)
        assertEquals(report.activity, loaded[0].activity)
        assertEquals(report.llmSummary, loaded[0].llmSummary)
    }

    @Test
    fun `add multiple reports`() = runTest {
        repository.add(makeReport("r-1"))
        repository.add(makeReport("r-2"))
        repository.add(makeReport("r-3"))
        val loaded = repository.loadAll()
        assertEquals(3, loaded.size)
    }

    @Test
    fun `deleteByJobId removes matching reports`() = runTest {
        repository.add(makeReport("r-1", jobId = "j-1"))
        repository.add(makeReport("r-2", jobId = "j-1"))
        repository.add(makeReport("r-3", jobId = "j-2"))
        repository.deleteByJobId("j-1")
        val loaded = repository.loadAll()
        assertEquals(1, loaded.size)
        assertEquals("j-2", loaded[0].jobId)
    }

    @Test
    fun `loadAll handles corrupted file gracefully`() = runTest {
        tempDir.resolve("bored_reports.json").writeText("{broken}")
        val result = repository.loadAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `loadAll handles empty file`() = runTest {
        tempDir.resolve("bored_reports.json").writeText("")
        val result = repository.loadAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `add preserves all fields`() = runTest {
        val report = BoredReportItem(
            id = "full-test",
            jobId = "j-full",
            activity = "Learn origami",
            llmSummary = "Попробуйте оригами для расслабления",
            createdAtEpochMs = 1700000000000L
        )
        repository.add(report)
        val loaded = repository.loadAll()
        assertEquals(1, loaded.size)
        assertEquals(report.id, loaded[0].id)
        assertEquals(report.jobId, loaded[0].jobId)
        assertEquals(report.activity, loaded[0].activity)
        assertEquals(report.llmSummary, loaded[0].llmSummary)
        assertEquals(report.createdAtEpochMs, loaded[0].createdAtEpochMs)
    }
}
