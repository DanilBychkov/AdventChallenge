package org.bothubclient.domain.entity

import java.util.*

enum class SummaryStatus {
    PENDING,
    COMPLETED,
    FAILED
}

data class SummaryBlock(
    val id: String = UUID.randomUUID().toString(),
    val originalMessageCount: Int,
    val originalMessages: List<Message>,
    val summary: String,
    val estimatedTokens: Int,
    val createdAt: String = java.time.LocalTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
    val status: SummaryStatus = SummaryStatus.COMPLETED,
    val errorMessage: String? = null
)
