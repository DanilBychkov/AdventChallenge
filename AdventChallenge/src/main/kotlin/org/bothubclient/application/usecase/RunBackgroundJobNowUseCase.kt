package org.bothubclient.application.usecase

import org.bothubclient.domain.repository.BackgroundJobRepository

class RunBackgroundJobNowUseCase(
    private val repository: BackgroundJobRepository,
    private val jobExecutor: suspend (String) -> Unit
) {
    suspend operator fun invoke(jobId: String): Boolean {
        val job = repository.findById(jobId) ?: return false
        jobExecutor(job.id)
        return true
    }
}
