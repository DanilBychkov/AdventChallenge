package org.bothubclient.application.mcp

import org.bothubclient.domain.entity.McpServerConfig

interface McpClient {
    suspend fun fetchContext(server: McpServerConfig, query: String): McpFetchResult

    suspend fun checkHealth(server: McpServerConfig): McpHealthResult
}

sealed class McpFetchResult {
    data class Success(val content: String) : McpFetchResult()

    data class Failure(
        val reason: String,
        val throwable: Throwable? = null
    ) : McpFetchResult()
}

sealed class McpHealthResult {
    data object Online : McpHealthResult()

    data class Offline(val message: String) : McpHealthResult()

    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : McpHealthResult()
}
