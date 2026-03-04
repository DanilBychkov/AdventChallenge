package org.bothubclient.domain.entity

import kotlinx.serialization.Serializable

@Serializable
enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
