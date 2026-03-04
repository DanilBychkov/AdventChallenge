package org.bothubclient.domain.entity

data class StateTransition(
    val from: TaskState,
    val to: TaskState,
    val condition: (TaskContext) -> Boolean,
    val action: suspend (TaskContext) -> TaskContext
)
