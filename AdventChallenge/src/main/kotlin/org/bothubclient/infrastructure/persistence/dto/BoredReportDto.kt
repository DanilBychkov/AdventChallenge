package org.bothubclient.infrastructure.persistence.dto

import kotlinx.serialization.Serializable
import org.bothubclient.domain.entity.BoredReportItem

@Serializable
data class BoredReportDto(
    val id: String,
    val jobId: String,
    val activity: String,
    val llmSummary: String,
    val createdAtEpochMs: Long
)

@Serializable
data class BoredReportsFileDto(
    val version: Int = 1,
    val reports: List<BoredReportDto> = emptyList()
)

fun BoredReportItem.toDto(): BoredReportDto = BoredReportDto(
    id = id,
    jobId = jobId,
    activity = activity,
    llmSummary = llmSummary,
    createdAtEpochMs = createdAtEpochMs
)

fun BoredReportDto.toDomain(): BoredReportItem = BoredReportItem(
    id = id,
    jobId = jobId,
    activity = activity,
    llmSummary = llmSummary,
    createdAtEpochMs = createdAtEpochMs
)
