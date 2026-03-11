package org.bothubclient.application.usecase

import org.bothubclient.domain.entity.BackgroundJob
import org.bothubclient.domain.entity.JobStatus
import org.bothubclient.domain.entity.JobType
import org.bothubclient.domain.repository.BackgroundJobRepository
import java.util.*

class ConfigureBackgroundJobUseCase(
    private val repository: BackgroundJobRepository
) {
    suspend operator fun invoke(
        intervalMinutes: Int,
        enabled: Boolean = true,
        type: JobType = JobType.BORED_REPORT
    ): BackgroundJob {
        val existing = repository.loadAll().firstOrNull { it.type == type }
        val now = System.currentTimeMillis()

        val job = if (existing != null) {
            existing.copy(
                intervalMinutes = intervalMinutes,
                enabled = enabled,
                status = if (enabled) JobStatus.ACTIVE else JobStatus.PAUSED,
                nextRunEpochMs = now + intervalMinutes * 60_000L
            )
        } else {
            BackgroundJob(
                id = UUID.randomUUID().toString(),
                type = type,
                intervalMinutes = intervalMinutes,
                enabled = enabled,
                status = if (enabled) JobStatus.ACTIVE else JobStatus.PAUSED,
                nextRunEpochMs = now + intervalMinutes * 60_000L,
                lastRunEpochMs = null,
                lastError = null,
                createdAtEpochMs = now
            )
        }

        repository.save(job)
        return job
    }
}
