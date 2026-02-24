package org.bothubclient.domain.repository

import org.bothubclient.domain.entity.Message

interface ChatHistoryStorage {
    suspend fun loadHistory(sessionId: String): List<Message>
    suspend fun saveHistory(sessionId: String, messages: List<Message>)
    suspend fun deleteHistory(sessionId: String)
    suspend fun listSessions(): List<String>
}
