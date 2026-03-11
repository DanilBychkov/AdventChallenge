package org.bothubclient.infrastructure.persistence.dto

import kotlinx.serialization.Serializable
import org.bothubclient.domain.entity.BackgroundJob
import org.bothubclient.domain.entity.JobStatus
import org.bothubclient.domain.entity.JobType

@Serializable
data class BackgroundJobDto(
    val id: String,
    val type: String,
    val intervalMinutes: Int,
    val enabled: Boolean,
    val status: String,
    val nextRunEpochMs: Long,
    val lastRunEpochMs: Long? = null,
    val lastError: String? = null,
    val createdAtEpochMs: Long,
    val retryCount: Int = 0
)

@Serializable
data class BackgroundJobsFileDto(
    val version: Int = 1,
    val jobs: List<BackgroundJobDto> = emptyList()
)

fun BackgroundJob.toDto(): BackgroundJobDto = BackgroundJobDto(
    id = id,
    type = type.name,
    intervalMinutes = intervalMinutes,
    enabled = enabled,
    status = status.name,
    nextRunEpochMs = nextRunEpochMs,
    lastRunEpochMs = lastRunEpochMs,
    lastError = lastError,
    createdAtEpochMs = createdAtEpochMs,
    retryCount = retryCount
)

fun BackgroundJobDto.toDomain(): BackgroundJob = BackgroundJob(
    id = id,
    type = runCatching { JobType.valueOf(type) }.getOrDefault(JobType.BORED_REPORT),
    intervalMinutes = intervalMinutes,
    enabled = enabled,
    status = runCatching { JobStatus.valueOf(status) }.getOrDefault(JobStatus.PAUSED),
    nextRunEpochMs = nextRunEpochMs,
    lastRunEpochMs = lastRunEpochMs,
    lastError = lastError,
    createdAtEpochMs = createdAtEpochMs,
    retryCount = retryCount
)
