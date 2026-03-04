package org.bothubclient.domain.entity

import kotlinx.serialization.Serializable

@Serializable
enum class TaskState {
    IDLE,
    PLANNING,
    EXECUTION,
    VALIDATION,
    DONE
}
