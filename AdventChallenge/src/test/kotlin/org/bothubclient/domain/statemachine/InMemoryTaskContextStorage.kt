package org.bothubclient.domain.statemachine

import org.bothubclient.domain.entity.TaskContext
import org.bothubclient.domain.repository.TaskContextStorage

class InMemoryTaskContextStorage : TaskContextStorage {
    private val map = mutableMapOf<String, TaskContext?>()
    private val lock = Any()

    override suspend fun load(sessionId: String, branchId: String): TaskContext? =
        synchronized(lock) { map[key(sessionId, branchId)] }

    override suspend fun save(sessionId: String, branchId: String, context: TaskContext?) {
        synchronized(lock) { map[key(sessionId, branchId)] = context }
    }

    override suspend fun delete(sessionId: String, branchId: String) {
        synchronized(lock) { map.remove(key(sessionId, branchId)) }
    }

    private fun key(sessionId: String, branchId: String): String = "$sessionId::$branchId"
}
