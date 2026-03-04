package org.bothubclient.domain.repository

import org.bothubclient.domain.entity.TaskContext

interface TaskContextStorage {
    suspend fun load(sessionId: String, branchId: String): TaskContext?
    suspend fun save(sessionId: String, branchId: String, context: TaskContext?)
    suspend fun delete(sessionId: String, branchId: String)
}
