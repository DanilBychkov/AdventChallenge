package org.bothubclient.application.usecase

import org.bothubclient.domain.entity.McpServerConfig
import org.bothubclient.domain.repository.McpRegistry

/**
 * Use case for updating an MCP server configuration.
 * Uses atomic update to prevent race conditions.
 */
class UpdateMcpServerUseCase(
    private val registry: McpRegistry
) {
    /**
     * Updates a single MCP server configuration by id.
     * Atomically reads, modifies, and writes the configuration.
     */
    suspend operator fun invoke(server: McpServerConfig) {
        registry.runAtomicUpdate { list ->
            val updated = list.map { existing ->
                if (existing.id == server.id) server else existing
            }
            Pair(updated, Unit)
        }
    }
}
