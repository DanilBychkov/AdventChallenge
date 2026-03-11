package org.bothubclient.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.bothubclient.application.usecase.ConfigureBackgroundJobUseCase
import org.bothubclient.application.usecase.ListBackgroundJobsUseCase
import org.bothubclient.application.usecase.ListBoredReportsUseCase
import org.bothubclient.domain.entity.BackgroundJob
import org.bothubclient.domain.entity.BoredReportItem
import org.bothubclient.domain.entity.JobStatus
import org.bothubclient.domain.repository.BackgroundJobRepository
import org.bothubclient.domain.repository.BoredClient
import org.bothubclient.domain.repository.BoredReportRepository
import org.bothubclient.domain.repository.ReportGenerator
import org.bothubclient.infrastructure.scheduler.BackgroundJobManager
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundJobIntegrationTest {

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

    private val activities = listOf(
        "Learn to paint",
        "Go for a hike",
        "Read a book"
    )

    private class CyclingBoredClient(private val activities: List<String>) : BoredClient {
        var idx = 0
        override suspend fun getRandomActivity(): String = activities[idx++ % activities.size]
    }

    private class FakeReportGenerator : ReportGenerator {
        override suspend fun generateReport(activity: String) =
            "Рекомендация: $activity"
    }

    @Test
    fun `full cycle -- configure job, 3 ticks, verify reports`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val reportRepo = InMemoryReportRepo()
        val boredClient = CyclingBoredClient(activities)
        val reportGen = FakeReportGenerator()
        var now = 0L

        val manager = BackgroundJobManager(
            jobRepo = jobRepo,
            reportRepo = reportRepo,
            boredClient = boredClient,
            reportGenerator = reportGen,
            scope = this,
            clock = { now },
            tickIntervalMs = 30_000L
        )

        val configureUseCase = ConfigureBackgroundJobUseCase(jobRepo)
        val listJobsUseCase = ListBackgroundJobsUseCase(jobRepo)
        val listReportsUseCase = ListBoredReportsUseCase(reportRepo)

        val job = configureUseCase(intervalMinutes = 1, enabled = true)
        assertNotNull(job)
        assertEquals(1, job.intervalMinutes)
        assertTrue(job.enabled)

        now = job.nextRunEpochMs + 1
        manager.start()
        advanceTimeBy(35_000L)
        assertEquals(1, reportRepo.reports.size)

        val afterFirst = jobRepo.findById(job.id)
        assertNotNull(afterFirst)
        now = afterFirst.nextRunEpochMs + 1
        advanceTimeBy(35_000L)
        assertEquals(2, reportRepo.reports.size)

        val afterSecond = jobRepo.findById(job.id)
        assertNotNull(afterSecond)
        now = afterSecond.nextRunEpochMs + 1
        advanceTimeBy(35_000L)
        assertEquals(3, reportRepo.reports.size)

        manager.stop()

        val allJobs = listJobsUseCase()
        assertEquals(1, allJobs.size)
        assertEquals(JobStatus.ACTIVE, allJobs[0].status)

        val allReports = listReportsUseCase()
        assertEquals(3, allReports.size)
        assertEquals("Learn to paint", allReports[2].activity)
        assertEquals("Go for a hike", allReports[1].activity)
        assertEquals("Read a book", allReports[0].activity)
    }

    @Test
    fun `configure updates existing job interval`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val configureUseCase = ConfigureBackgroundJobUseCase(jobRepo)

        val job1 = configureUseCase(intervalMinutes = 5)
        val job2 = configureUseCase(intervalMinutes = 15)

        assertEquals(job1.id, job2.id)
        assertEquals(15, job2.intervalMinutes)
        assertEquals(1, jobRepo.loadAll().size)
    }

    @Test
    fun `error recovery with retries`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val reportRepo = InMemoryReportRepo()
        val failingClient = object : BoredClient {
            var calls = 0
            override suspend fun getRandomActivity(): String {
                calls++
                if (calls <= 2) throw RuntimeException("Temporary failure")
                return "Success activity"
            }
        }
        var now = 0L

        val manager = BackgroundJobManager(
            jobRepo = jobRepo,
            reportRepo = reportRepo,
            boredClient = failingClient,
            reportGenerator = FakeReportGenerator(),
            scope = this,
            clock = { now },
            tickIntervalMs = 30_000L
        )

        val configureUseCase = ConfigureBackgroundJobUseCase(jobRepo)
        val job = configureUseCase(intervalMinutes = 1)

        now = job.nextRunEpochMs + 1
        manager.start()
        advanceTimeBy(35_000L)

        val afterError = jobRepo.findById(job.id)
        assertNotNull(afterError)
        assertEquals(JobStatus.ERROR, afterError.status)
        assertEquals(1, afterError.retryCount)
        assertTrue(reportRepo.reports.isEmpty())

        now = afterError.nextRunEpochMs + 1
        advanceTimeBy(35_000L)

        val afterSecondError = jobRepo.findById(job.id)
        assertNotNull(afterSecondError)
        assertEquals(JobStatus.ERROR, afterSecondError.status)
        assertEquals(2, afterSecondError.retryCount)

        now = afterSecondError.nextRunEpochMs + 1
        advanceTimeBy(35_000L)

        val afterSuccess = jobRepo.findById(job.id)
        assertNotNull(afterSuccess)
        assertEquals(JobStatus.ACTIVE, afterSuccess.status)
        assertEquals(0, afterSuccess.retryCount)
        assertEquals(1, reportRepo.reports.size)

        manager.stop()
    }
}
