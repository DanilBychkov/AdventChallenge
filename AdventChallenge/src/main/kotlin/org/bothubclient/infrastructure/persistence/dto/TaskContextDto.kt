package org.bothubclient.infrastructure.persistence.dto

import kotlinx.serialization.Serializable
import org.bothubclient.domain.entity.TaskContext

@Serializable
data class TaskContextDto(
    val version: Int = 1,
    val sessionId: String,
    val branchId: String,
    val context: TaskContext
)
