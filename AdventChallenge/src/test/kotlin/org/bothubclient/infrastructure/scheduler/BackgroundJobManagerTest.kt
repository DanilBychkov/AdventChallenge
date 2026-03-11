package org.bothubclient.infrastructure.scheduler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.entity.BackgroundJob
import org.bothubclient.domain.entity.BoredReportItem
import org.bothubclient.domain.entity.JobStatus
import org.bothubclient.domain.entity.JobType
import org.bothubclient.domain.repository.BackgroundJobRepository
import org.bothubclient.domain.repository.BoredClient
import org.bothubclient.domain.repository.BoredReportRepository
import org.bothubclient.domain.repository.ReportGenerator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundJobManagerTest {

    private class InMemoryJobRepo : BackgroundJobRepository {
        val jobs = mutableMapOf<String, BackgroundJob>()
        override suspend fun loadAll() = jobs.values.toList()
        override suspend fun save(job: BackgroundJob) {
            jobs[job.id] = job
        }

        override suspend fun findById(id: String) = jobs[id]
        override suspend fun delete(id: String) {
            jobs.remove(id)
        }
    }

    private class InMemoryReportRepo : BoredReportRepository {
        val reports = mutableListOf<BoredReportItem>()
        override suspend fun loadAll() = reports.toList()
        override suspend fun add(report: BoredReportItem) {
            reports.add(report)
        }

        override suspend fun deleteByJobId(jobId: String) {
            reports.removeAll { it.jobId == jobId }
        }
    }

    private class FakeBoredClient(private val activity: String = "Learn something") : BoredClient {
        var callCount = 0
        override suspend fun getRandomActivity(): String {
            callCount++
            return activity
        }
    }

    private class FakeReportGenerator(private val summary: String = "Рекомендация") : ReportGenerator {
        var callCount = 0
        override suspend fun generateReport(activity: String): String {
            callCount++
            return summary
        }
    }

    private class FailingBoredClient : BoredClient {
        override suspend fun getRandomActivity(): String = throw RuntimeException("Network error")
    }

    private fun createManager(
        testScope: TestScope,
        jobRepo: InMemoryJobRepo,
        reportRepo: InMemoryReportRepo,
        boredClient: BoredClient = FakeBoredClient(),
        reportGenerator: ReportGenerator = FakeReportGenerator(),
        clock: () -> Long
    ) = BackgroundJobManager(
        jobRepo = jobRepo,
        reportRepo = reportRepo,
        boredClient = boredClient,
        reportGenerator = reportGenerator,
        scope = testScope,
        clock = clock,
        tickIntervalMs = 30_000L
    )

    private fun makeJob(
        id: String = "test-job",
        intervalMinutes: Int = 1,
        enabled: Boolean = true,
        nextRunEpochMs: Long = 0L,
        status: JobStatus = JobStatus.ACTIVE
    ) = BackgroundJob(
        id = id,
        type = JobType.BORED_REPORT,
        intervalMinutes = intervalMinutes,
        enabled = enabled,
        status = status,
        nextRunEpochMs = nextRunEpochMs,
        lastRunEpochMs = null,
        lastError = null,
        createdAtEpochMs = 0L
    )

    @Test
    fun `due job executes and generates report`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val reportRepo = InMemoryReportRepo()
        val boredClient = FakeBoredClient("Paint a picture")
        val reportGen = FakeReportGenerator("Нарисуй картину!")
        var now = 100_000L

        val manager = createManager(
            testScope = this,
            jobRepo = jobRepo,
            reportRepo = reportRepo,
            boredClient = boredClient,
            reportGenerator = reportGen,
            clock = { now }
        )

        jobRepo.save(makeJob(nextRunEpochMs = 50_000L))
        manager.start()
        advanceTimeBy(35_000L)

        assertEquals(1, reportRepo.reports.size)
        assertEquals("Paint a picture", reportRepo.reports[0].activity)
        assertEquals("Нарисуй картину!", reportRepo.reports[0].llmSummary)

        val updated = jobRepo.findById("test-job")
        assertNotNull(updated)
        assertEquals(JobStatus.ACTIVE, updated.status)
        assertNotNull(updated.lastRunEpochMs)
        assertEquals(0, updated.retryCount)

        manager.stop()
    }

    @Test
    fun `disabled job is skipped`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val reportRepo = InMemoryReportRepo()
        val boredClient = FakeBoredClient()
        var now = 100_000L

        val manager = createManager(
            testScope = this,
            jobRepo = jobRepo,
            reportRepo = reportRepo,
            boredClient = boredClient,
            clock = { now }
        )

        jobRepo.save(makeJob(enabled = false, nextRunEpochMs = 50_000L))
        manager.start()
        advanceTimeBy(35_000L)

        assertTrue(reportRepo.reports.isEmpty())
        assertEquals(0, boredClient.callCount)

        manager.stop()
    }

    @Test
    fun `error sets status and increments retryCount`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val reportRepo = InMemoryReportRepo()
        var now = 100_000L

        val manager = createManager(
            testScope = this,
            jobRepo = jobRepo,
            reportRepo = reportRepo,
            boredClient = FailingBoredClient(),
            clock = { now }
        )

        jobRepo.save(makeJob(nextRunEpochMs = 50_000L))
        manager.start()
        advanceTimeBy(35_000L)

        val updated = jobRepo.findById("test-job")
        assertNotNull(updated)
        assertEquals(JobStatus.ERROR, updated.status)
        assertEquals(1, updated.retryCount)
        assertNotNull(updated.lastError)
        assertTrue(updated.lastError!!.contains("Network error"))
        assertTrue(reportRepo.reports.isEmpty())

        manager.stop()
    }

    @Test
    fun `multiple ticks generate exactly 3 reports for 3 due intervals`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val reportRepo = InMemoryReportRepo()
        var now = 0L

        val manager = createManager(
            testScope = this,
            jobRepo = jobRepo,
            reportRepo = reportRepo,
            clock = { now }
        )

        jobRepo.save(makeJob(intervalMinutes = 1, nextRunEpochMs = 25_000L))
        manager.start()

        // Tick 1: job is due (nextRun=25s, now=30s)
        now = 30_000L
        advanceTimeBy(35_000L)
        assertEquals(1, reportRepo.reports.size, "Expected 1 report after first tick")

        // Tick 2: advance clock past nextRun (should be ~90s after first run)
        val afterFirst = jobRepo.findById("test-job")
        assertNotNull(afterFirst)
        now = afterFirst.nextRunEpochMs + 1_000L
        advanceTimeBy(35_000L)
        assertEquals(2, reportRepo.reports.size, "Expected 2 reports after second tick")

        // Tick 3: advance clock past nextRun again
        val afterSecond = jobRepo.findById("test-job")
        assertNotNull(afterSecond)
        now = afterSecond.nextRunEpochMs + 1_000L
        advanceTimeBy(35_000L)
        assertEquals(3, reportRepo.reports.size, "Expected 3 reports after third tick")

        manager.stop()
    }

    @Test
    fun `onReportGenerated callback fires on each execution`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val reportRepo = InMemoryReportRepo()
        val callbackReports = mutableListOf<BoredReportItem>()
        var now = 0L

        val manager = createManager(
            testScope = this,
            jobRepo = jobRepo,
            reportRepo = reportRepo,
            clock = { now }
        )
        manager.onReportGenerated = { report -> callbackReports.add(report) }

        jobRepo.save(makeJob(intervalMinutes = 1, nextRunEpochMs = 25_000L))
        manager.start()

        // Tick 1
        now = 30_000L
        advanceTimeBy(35_000L)
        assertEquals(1, callbackReports.size, "Callback should fire once after first tick")
        assertEquals("Learn something", callbackReports[0].activity)

        // Tick 2
        val afterFirst = jobRepo.findById("test-job")
        assertNotNull(afterFirst)
        now = afterFirst.nextRunEpochMs + 1_000L
        advanceTimeBy(35_000L)
        assertEquals(2, callbackReports.size, "Callback should fire twice after second tick")

        manager.stop()
    }

    @Test
    fun `onReportGenerated callback NOT fired on error`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val reportRepo = InMemoryReportRepo()
        val callbackReports = mutableListOf<BoredReportItem>()
        var now = 100_000L

        val manager = createManager(
            testScope = this,
            jobRepo = jobRepo,
            reportRepo = reportRepo,
            boredClient = FailingBoredClient(),
            clock = { now }
        )
        manager.onReportGenerated = { report -> callbackReports.add(report) }

        jobRepo.save(makeJob(nextRunEpochMs = 50_000L))
        manager.start()
        advanceTimeBy(35_000L)

        assertTrue(callbackReports.isEmpty(), "Callback must NOT fire when job fails")
        manager.stop()
    }

    @Test
    fun `runJobNow also triggers callback`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val reportRepo = InMemoryReportRepo()
        val callbackReports = mutableListOf<BoredReportItem>()
        var now = 0L

        val manager = createManager(
            testScope = this,
            jobRepo = jobRepo,
            reportRepo = reportRepo,
            clock = { now }
        )
        manager.onReportGenerated = { report -> callbackReports.add(report) }

        jobRepo.save(makeJob(nextRunEpochMs = 999_999_999L))
        manager.runJobNow("test-job")

        assertEquals(1, callbackReports.size, "Callback should fire on runJobNow")
        assertEquals(1, reportRepo.reports.size)
    }

    @Test
    fun `runJobNow executes immediately`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val reportRepo = InMemoryReportRepo()
        var now = 0L

        val manager = createManager(
            testScope = this,
            jobRepo = jobRepo,
            reportRepo = reportRepo,
            clock = { now }
        )

        jobRepo.save(makeJob(nextRunEpochMs = 999_999_999L))
        manager.runJobNow("test-job")

        assertEquals(1, reportRepo.reports.size)
    }

    @Test
    fun `job not yet due is not executed`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val reportRepo = InMemoryReportRepo()
        var now = 0L

        val manager = createManager(
            testScope = this,
            jobRepo = jobRepo,
            reportRepo = reportRepo,
            clock = { now }
        )

        jobRepo.save(makeJob(nextRunEpochMs = 999_999_999L))
        manager.start()
        advanceTimeBy(35_000L)

        assertTrue(reportRepo.reports.isEmpty())
        manager.stop()
    }
}
