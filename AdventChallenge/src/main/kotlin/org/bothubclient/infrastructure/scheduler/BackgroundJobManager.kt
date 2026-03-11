package org.bothubclient.infrastructure.scheduler

import kotlinx.coroutines.*
import org.bothubclient.domain.entity.BoredReportItem
import org.bothubclient.domain.entity.JobStatus
import org.bothubclient.domain.repository.BackgroundJobRepository
import org.bothubclient.domain.repository.BoredClient
import org.bothubclient.domain.repository.BoredReportRepository
import org.bothubclient.domain.repository.ReportGenerator
import org.bothubclient.infrastructure.logging.AppLogger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class BackgroundJobManager(
    private val jobRepo: BackgroundJobRepository,
    private val reportRepo: BoredReportRepository,
    private val boredClient: BoredClient,
    private val reportGenerator: ReportGenerator,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val tickIntervalMs: Long = 30_000L
) {
    companion object {
        private const val TAG = "BackgroundJobManager"
    }

    var onReportGenerated: ((BoredReportItem) -> Unit)? = null

    private var tickerJob: Job? = null
    private val runningJobs = ConcurrentHashMap<String, Boolean>()

    fun start() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            AppLogger.i(TAG, "Scheduler started, tick interval=${tickIntervalMs}ms")
            while (isActive) {
                delay(tickIntervalMs)
                tickDueJobs()
            }
        }
    }

    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
        AppLogger.i(TAG, "Scheduler stopped")
    }

    suspend fun runJobNow(jobId: String) {
        executeJob(jobId)
    }

    private suspend fun tickDueJobs() {
        val now = clock()
        val jobs = runCatching { jobRepo.loadAll() }.getOrElse { e ->
            AppLogger.e(TAG, "Failed to load jobs for tick", e)
            return
        }
        for (job in jobs) {
            if (job.enabled && job.nextRunEpochMs <= now && job.status != JobStatus.RUNNING) {
                scope.launch { executeJob(job.id) }
            }
        }
    }

    private suspend fun executeJob(jobId: String) {
        if (runningJobs.putIfAbsent(jobId, true) != null) {
            AppLogger.i(TAG, "Job $jobId already running, skipping")
            return
        }

        try {
            val job = jobRepo.findById(jobId) ?: run {
                AppLogger.e(TAG, "Job $jobId not found", null)
                return
            }

            jobRepo.save(job.copy(status = JobStatus.RUNNING))
            AppLogger.i(TAG, "Executing job $jobId (type=${job.type})")

            val activity = boredClient.getRandomActivity()
            val summary = reportGenerator.generateReport(activity)

            val now = clock()
            val report = BoredReportItem(
                id = UUID.randomUUID().toString(),
                jobId = jobId,
                activity = activity,
                llmSummary = summary,
                createdAtEpochMs = now
            )
            reportRepo.add(report)

            val updatedJob = job.copy(
                status = JobStatus.ACTIVE,
                lastRunEpochMs = now,
                nextRunEpochMs = now + job.intervalMinutes * 60_000L,
                lastError = null,
                retryCount = 0
            )
            jobRepo.save(updatedJob)
            AppLogger.i(TAG, "Job $jobId completed, report=${report.id}")

            runCatching { onReportGenerated?.invoke(report) }.onFailure { e ->
                AppLogger.e(TAG, "onReportGenerated callback failed", e)
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Job $jobId failed", e)
            val job = jobRepo.findById(jobId) ?: return
            val now = clock()
            val newRetryCount = job.retryCount + 1
            val backoffMs = minOf(
                job.intervalMinutes * 60_000L,
                (1L shl newRetryCount.coerceAtMost(10)) * 60_000L
            )
            val errorJob = job.copy(
                status = JobStatus.ERROR,
                lastError = e.message ?: "Unknown error",
                retryCount = newRetryCount,
                nextRunEpochMs = now + backoffMs
            )
            runCatching { jobRepo.save(errorJob) }
        } finally {
            runningJobs.remove(jobId)
        }
    }
}
