package org.bothubclient.domain.entity

data class BoredReportItem(
    val id: String,
    val jobId: String,
    val activity: String,
    val llmSummary: String,
    val createdAtEpochMs: Long
)
