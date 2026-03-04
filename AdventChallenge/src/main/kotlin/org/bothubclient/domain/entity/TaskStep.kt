package org.bothubclient.domain.entity

import kotlinx.serialization.Serializable

@Serializable
data class TaskStep(
    val id: String,
    val description: String,
    val status: StepStatus,
    val result: String? = null
)
