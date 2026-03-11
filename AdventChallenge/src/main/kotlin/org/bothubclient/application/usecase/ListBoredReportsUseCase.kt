package org.bothubclient.application.usecase

import org.bothubclient.domain.entity.BoredReportItem
import org.bothubclient.domain.repository.BoredReportRepository

class ListBoredReportsUseCase(
    private val repository: BoredReportRepository
) {
    suspend operator fun invoke(): List<BoredReportItem> =
        repository.loadAll().sortedByDescending { it.createdAtEpochMs }
}
