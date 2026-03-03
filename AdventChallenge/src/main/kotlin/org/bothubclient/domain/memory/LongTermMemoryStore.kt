package org.bothubclient.domain.memory

interface LongTermMemoryStore {
    suspend fun upsert(item: MemoryItem): Boolean

    suspend fun search(query: String, limit: Int = 12): List<MemoryItem>

    suspend fun snapshot(): List<MemoryItem>

    suspend fun deleteWhere(criteria: (MemoryItem) -> Boolean): Int
}

