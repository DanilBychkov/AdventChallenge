package org.bothubclient.domain.entity

enum class JobType { BORED_REPORT }

enum class JobStatus { ACTIVE, PAUSED, RUNNING, ERROR }

data class BackgroundJob(
    val id: String,
    val type: JobType,
    val intervalMinutes: Int,
    val enabled: Boolean,
    val status: JobStatus,
    val nextRunEpochMs: Long,
    val lastRunEpochMs: Long?,
    val lastError: String?,
    val createdAtEpochMs: Long,
    val retryCount: Int = 0
)
