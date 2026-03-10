package org.bothubclient.application.usecase

import org.bothubclient.application.mcp.McpClient
import org.bothubclient.application.mcp.McpHealthResult
import org.bothubclient.domain.entity.McpHealthStatus
import org.bothubclient.domain.logging.Logger
import org.bothubclient.domain.logging.NoOpLogger
import org.bothubclient.domain.repository.McpRegistry

/**
 * Use case for checking MCP server health.
 */
class CheckMcpHealthUseCase(
    private val registry: McpRegistry,
    private val mcpClient: McpClient,
    private val logger: Logger = NoOpLogger
) {
    suspend operator fun invoke(serverId: String): McpHealthStatus {
        val server = registry.getById(serverId)
        if (server == null) {
            logger.log("CheckMcpHealthUseCase", "MCP health server=$serverId success=false reason=Server not found")
            return McpHealthStatus.ERROR
        }

        val healthResult = runCatching {
            mcpClient.checkHealth(server)
        }.getOrElse { throwable ->
            McpHealthResult.Error(
                message = "Healthcheck failed with exception: ${throwable.message ?: "unknown"}",
                throwable = throwable
            )
        }

        val status = when (healthResult) {
            is McpHealthResult.Online -> McpHealthStatus.ONLINE
            is McpHealthResult.Offline -> McpHealthStatus.OFFLINE
            is McpHealthResult.Error -> McpHealthStatus.ERROR
        }

        val updatedServer = server.withHealthStatus(status)
        return runCatching {
            registry.runAtomicUpdate { list ->
                val updated = list.map { existing ->
                    if (existing.id == serverId) updatedServer else existing
                }
                Pair(updated, status)
            }.also {
                logger.log("CheckMcpHealthUseCase", "MCP health server=$serverId success=true status=$status")
            }
        }.getOrElse { throwable ->
            logger.log(
                "CheckMcpHealthUseCase",
                "MCP health server=$serverId success=false reason=Failed to persist health status: ${throwable.message}"
            )
            McpHealthStatus.ERROR
        }
    }
}
