package org.bothubclient.domain.agent

import org.bothubclient.domain.entity.*
import org.bothubclient.domain.memory.MemoryItem

interface ChatAgentIntrospection {
    fun updateConfig(newConfig: ContextConfig)

    fun getSummaryBlocks(sessionId: String): List<SummaryBlock>

    fun getBranchIds(sessionId: String): List<String>

    fun getActiveBranchId(sessionId: String): String

    fun setActiveBranch(sessionId: String, branchId: String)

    fun createBranchFromCheckpoint(sessionId: String, checkpointSize: Int): String

    fun getWorkingMemory(sessionId: String): Map<WmCategory, Map<String, FactEntry>>

    fun setWorkingMemoryEntry(
        sessionId: String,
        category: WmCategory,
        key: String,
        value: String,
        confidence: Float = 1.0f
    )

    fun deleteWorkingMemoryEntry(sessionId: String, category: WmCategory, key: String): Boolean

    fun getStmCount(sessionId: String): Int

    fun getShortTermMessages(sessionId: String): List<Message>

    fun clearShortTermMemory(sessionId: String): Int

    suspend fun getLongTermMemorySnapshot(): List<MemoryItem>

    suspend fun saveToLtm(category: WmCategory, key: String, value: String, confidence: Float = 1.0f): Boolean

    suspend fun findInLtm(query: String, limit: Int = 10): List<MemoryItem>

    suspend fun deleteFromLtmByKey(key: String): Int

    fun getComposedContext(sessionId: String, systemPrompt: String, userMessage: String): ComposedContext

    fun getTaskContextSnapshot(sessionId: String): TaskContext?

    suspend fun approveTaskPlan(sessionId: String): TaskContext?

    suspend fun approveTaskValidation(sessionId: String): TaskContext?

    suspend fun resetTask(sessionId: String)

    fun getMetricsSnapshot(sessionId: String): AgentMetricsSnapshot
}
