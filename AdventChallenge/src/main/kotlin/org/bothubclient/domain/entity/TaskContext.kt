package org.bothubclient.domain.entity

import kotlinx.serialization.Serializable

@Serializable
data class TaskContext(
    val taskId: String,
    val state: TaskState,
    val originalRequest: String,
    val plan: List<TaskStep>,
    val currentStepIndex: Int,
    val artifacts: Map<String, String>,
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
