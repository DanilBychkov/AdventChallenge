package org.bothubclient.application.usecase

import org.bothubclient.domain.entity.BackgroundJob
import org.bothubclient.domain.entity.JobStatus
import org.bothubclient.domain.repository.BackgroundJobRepository

class ToggleBackgroundJobUseCase(
    private val repository: BackgroundJobRepository
) {
    suspend operator fun invoke(jobId: String, enabled: Boolean): BackgroundJob? {
        val job = repository.findById(jobId) ?: return null
        val updated = job.copy(
            enabled = enabled,
            status = if (enabled) JobStatus.ACTIVE else JobStatus.PAUSED
        )
        repository.save(updated)
        return updated
    }
}
