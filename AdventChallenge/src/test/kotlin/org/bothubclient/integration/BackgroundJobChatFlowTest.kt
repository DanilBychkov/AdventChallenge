package org.bothubclient.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.bothubclient.application.usecase.*
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
class BackgroundJobChatFlowTest {

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

    private val activities = listOf("Learn origami", "Go hiking", "Read a book", "Paint a picture")
    private var activityIdx = 0

    private inner class CyclingBoredClient : BoredClient {
        override suspend fun getRandomActivity() = activities[activityIdx++ % activities.size]
    }

    private class FakeReportGenerator : ReportGenerator {
        override suspend fun generateReport(activity: String) = "Рекомендация: $activity"
    }

    @Test
    fun `intent extraction creates job and callback fires on each periodic tick`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val reportRepo = InMemoryReportRepo()
        val chatMessages = mutableListOf<String>()
        var now = 0L

        val manager = BackgroundJobManager(
            jobRepo = jobRepo,
            reportRepo = reportRepo,
            boredClient = CyclingBoredClient(),
            reportGenerator = FakeReportGenerator(),
            scope = this,
            clock = { now },
            tickIntervalMs = 30_000L
        )
        manager.onReportGenerated = { report ->
            chatMessages.add("Фоновый отчёт: ${report.llmSummary}")
        }

        val parseUseCase = ParseScheduleIntentUseCase(llmExtractor = null)
        val configureUseCase = ConfigureBackgroundJobUseCase(jobRepo)

        val userMessage = "каждую минуту подсказывай что делать"
        val intent = parseUseCase(userMessage)
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, intent.intent)
        assertEquals(1, intent.intervalMinutes)
        assertTrue(intent.confidence >= 0.75)

        val job = configureUseCase(
            intervalMinutes = intent.intervalMinutes!!,
            enabled = intent.enabled ?: true
        )
        assertNotNull(job)
        assertEquals(1, job.intervalMinutes)
        assertTrue(job.enabled)

        manager.start()

        // Tick 1: advance past nextRun
        now = job.nextRunEpochMs + 1
        advanceTimeBy(35_000L)
        assertEquals(1, chatMessages.size, "Expected 1 chat message after first tick")
        assertEquals(1, reportRepo.reports.size)
        assertTrue(chatMessages[0].contains("Learn origami"))

        // Tick 2
        val afterFirst = jobRepo.findById(job.id)!!
        now = afterFirst.nextRunEpochMs + 1
        advanceTimeBy(35_000L)
        assertEquals(2, chatMessages.size, "Expected 2 chat messages after second tick")
        assertTrue(chatMessages[1].contains("Go hiking"))

        // Tick 3
        val afterSecond = jobRepo.findById(job.id)!!
        now = afterSecond.nextRunEpochMs + 1
        advanceTimeBy(35_000L)
        assertEquals(3, chatMessages.size, "Expected 3 chat messages after third tick")
        assertTrue(chatMessages[2].contains("Read a book"))

        // Verify job status after 3 successful runs
        val finalJob = jobRepo.findById(job.id)!!
        assertEquals(JobStatus.ACTIVE, finalJob.status)
        assertEquals(0, finalJob.retryCount)

        manager.stop()
    }

    @Test
    fun `non-schedule message does NOT create any job`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val parseUseCase = ParseScheduleIntentUseCase(llmExtractor = null)
        val configureUseCase = ConfigureBackgroundJobUseCase(jobRepo)

        val userMessage = "привет, как дела?"
        val intent = parseUseCase(userMessage)
        assertEquals(ScheduleIntent.NONE, intent.intent)

        assertTrue(jobRepo.loadAll().isEmpty(), "No job should be created for non-schedule message")
    }

    @Test
    fun `toggle job stops periodic execution`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val reportRepo = InMemoryReportRepo()
        val callbackCount = mutableListOf<Int>()
        var now = 0L

        val manager = BackgroundJobManager(
            jobRepo = jobRepo,
            reportRepo = reportRepo,
            boredClient = CyclingBoredClient(),
            reportGenerator = FakeReportGenerator(),
            scope = this,
            clock = { now },
            tickIntervalMs = 30_000L
        )
        manager.onReportGenerated = { callbackCount.add(1) }

        val configureUseCase = ConfigureBackgroundJobUseCase(jobRepo)
        val toggleUseCase = ToggleBackgroundJobUseCase(jobRepo)

        val job = configureUseCase(intervalMinutes = 1)
        manager.start()

        // First execution
        now = job.nextRunEpochMs + 1
        advanceTimeBy(35_000L)
        assertEquals(1, callbackCount.size)

        // Disable the job
        toggleUseCase(job.id, enabled = false)
        val disabled = jobRepo.findById(job.id)!!
        assertEquals(JobStatus.PAUSED, disabled.status)

        // Advance time -- should NOT execute
        now += 120_000L
        advanceTimeBy(35_000L)
        assertEquals(1, callbackCount.size, "Disabled job should not produce new reports")

        // Re-enable
        toggleUseCase(job.id, enabled = true)

        // Advance time -- should execute now
        val reEnabled = jobRepo.findById(job.id)!!
        now = reEnabled.nextRunEpochMs + 1
        advanceTimeBy(35_000L)
        assertEquals(2, callbackCount.size, "Re-enabled job should execute")

        manager.stop()
    }

    @Test
    fun `runJobNow adds message to chat via callback`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val reportRepo = InMemoryReportRepo()
        val chatMessages = mutableListOf<String>()
        var now = 0L

        val manager = BackgroundJobManager(
            jobRepo = jobRepo,
            reportRepo = reportRepo,
            boredClient = CyclingBoredClient(),
            reportGenerator = FakeReportGenerator(),
            scope = this,
            clock = { now },
            tickIntervalMs = 30_000L
        )
        manager.onReportGenerated = { report ->
            chatMessages.add(report.llmSummary)
        }

        val configureUseCase = ConfigureBackgroundJobUseCase(jobRepo)
        val runNowUseCase = RunBackgroundJobNowUseCase(jobRepo) { id -> manager.runJobNow(id) }

        val job = configureUseCase(intervalMinutes = 5)

        runNowUseCase(job.id)
        assertEquals(1, chatMessages.size, "RunNow should trigger callback")
        assertEquals(1, reportRepo.reports.size)

        runNowUseCase(job.id)
        assertEquals(2, chatMessages.size, "Second RunNow should also trigger callback")
        assertEquals(2, reportRepo.reports.size)
    }

    @Test
    fun `update interval changes next execution time`() = runTest {
        val jobRepo = InMemoryJobRepo()
        val configureUseCase = ConfigureBackgroundJobUseCase(jobRepo)

        val job = configureUseCase(intervalMinutes = 1)
        val nextRun1 = job.nextRunEpochMs

        val updated = configureUseCase(intervalMinutes = 5)
        assertEquals(job.id, updated.id, "Should update same job, not create new")
        assertTrue(updated.nextRunEpochMs > nextRun1, "New nextRun should be later with longer interval")
        assertEquals(5, updated.intervalMinutes)
    }
}
