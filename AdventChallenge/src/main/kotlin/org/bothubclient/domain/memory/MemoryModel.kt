package org.bothubclient.domain.memory

import org.bothubclient.domain.entity.MemoryOperation
import org.bothubclient.domain.entity.Message

interface MemoryModel {
    fun observe(message: Message)

    suspend fun extract(message: String): List<MemoryOperation>

    suspend fun persist(item: MemoryItem): Boolean

    suspend fun recall(query: String, limit: Int = 12): List<MemoryItem>

    suspend fun forget(criteria: (MemoryItem) -> Boolean)
}
