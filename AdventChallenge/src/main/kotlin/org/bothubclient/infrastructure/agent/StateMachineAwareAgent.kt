package org.bothubclient.infrastructure.agent

import org.bothubclient.domain.agent.ChatAgent
import org.bothubclient.domain.entity.TaskContext
import org.bothubclient.domain.entity.TaskState
import org.bothubclient.domain.entity.TaskStep

class StateMachineAwareAgent(
    private val delegate: CompressingChatAgent
) : ChatAgent by delegate {

    fun getTaskState(sessionId: String): TaskState = delegate.getTaskState(sessionId)

    fun getCurrentStep(sessionId: String): TaskStep? = delegate.getCurrentStep(sessionId)

    suspend fun advanceTask(sessionId: String): TaskContext = delegate.advanceTask(sessionId)

    suspend fun resetTask(sessionId: String) = delegate.resetTask(sessionId)
}
