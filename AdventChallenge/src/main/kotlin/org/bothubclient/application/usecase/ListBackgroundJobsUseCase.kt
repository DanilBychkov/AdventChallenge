package org.bothubclient.application.usecase

import org.bothubclient.domain.entity.BackgroundJob
import org.bothubclient.domain.repository.BackgroundJobRepository

class ListBackgroundJobsUseCase(
    private val repository: BackgroundJobRepository
) {
    suspend operator fun invoke(): List<BackgroundJob> = repository.loadAll()
}
