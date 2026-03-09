package org.bothubclient.application.usecase

import org.bothubclient.domain.entity.McpHealthStatus
import org.bothubclient.domain.repository.McpRegistry
import org.bothubclient.infrastructure.persistence.FileMcpSettingsStorage

/**
 * Use case for checking MCP server health.
 * Currently a stub that sets health status to UNKNOWN.
 */
class CheckMcpHealthUseCase(
    private val registry: McpRegistry,
    private val storage: FileMcpSettingsStorage
) {
    /**
     * Performs a health check on the specified MCP server.
     * Stub implementation: sets healthStatus to UNKNOWN and updates lastHealthCheckAt.
     */
    suspend operator fun invoke(serverId: String) {
        val server = registry.getById(serverId) ?: return
        
        val updatedServer = server.withHealthStatus(McpHealthStatus.UNKNOWN)
        
        val current = registry.getAll()
        val updated = current.map { existing ->
            if (existing.id == serverId) updatedServer else existing
        }
        storage.saveServers(updated)
    }
}
