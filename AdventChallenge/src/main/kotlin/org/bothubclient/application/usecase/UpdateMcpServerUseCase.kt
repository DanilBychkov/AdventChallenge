package org.bothubclient.application.usecase

import org.bothubclient.config.McpPresets
import org.bothubclient.domain.entity.McpServerConfig
import org.bothubclient.domain.repository.McpRegistry
import org.bothubclient.infrastructure.persistence.FileMcpSettingsStorage

/**
 * Use case for updating an MCP server configuration.
 * Merges presets with stored configs, replaces by id, and saves.
 */
class UpdateMcpServerUseCase(
    private val registry: McpRegistry,
    private val storage: FileMcpSettingsStorage
) {
    /**
     * Updates a single MCP server configuration by id.
     * Gets all servers from registry, replaces the matching one, and saves.
     */
    suspend operator fun invoke(server: McpServerConfig) {
        val current = registry.getAll()
        val updated = current.map { existing ->
            if (existing.id == server.id) server else existing
        }
        storage.saveServers(updated)
    }
}
