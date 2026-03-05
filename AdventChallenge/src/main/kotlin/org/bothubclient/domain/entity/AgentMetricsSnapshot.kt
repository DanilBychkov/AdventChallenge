package org.bothubclient.domain.entity

data class AgentMetricsSnapshot(
    val sessionsCount: Int,
    val wmGroups: Int,
    val wmFacts: Int,
    val compressionAttempts: Long,
    val compressionSuccesses: Long,
    val compressionFailures: Long,
    val recallCandidates: Long,
    val recallHits: Long,
    val recallDuplicatesFiltered: Long
) {
    val compressionRate: Float
        get() = if (compressionAttempts <= 0) 0f else (compressionSuccesses.toFloat() / compressionAttempts.toFloat())
}
