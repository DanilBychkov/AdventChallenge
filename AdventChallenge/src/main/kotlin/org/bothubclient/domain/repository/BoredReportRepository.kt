package org.bothubclient.domain.repository

import org.bothubclient.domain.entity.BoredReportItem

interface BoredReportRepository {
    suspend fun loadAll(): List<BoredReportItem>
    suspend fun add(report: BoredReportItem)
    suspend fun deleteByJobId(jobId: String)
}
