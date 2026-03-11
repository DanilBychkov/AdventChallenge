package org.bothubclient.domain.repository

import org.bothubclient.domain.entity.BackgroundJob

interface BackgroundJobRepository {
    suspend fun loadAll(): List<BackgroundJob>
    suspend fun save(job: BackgroundJob)
    suspend fun findById(id: String): BackgroundJob?
    suspend fun delete(id: String)
}
