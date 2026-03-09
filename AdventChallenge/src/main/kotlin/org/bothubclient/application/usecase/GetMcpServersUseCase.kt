package org.bothubclient.application.usecase

import org.bothubclient.domain.entity.McpServerConfig
import org.bothubclient.domain.repository.McpRegistry

/**
 * Use case for retrieving all MCP server configurations.
 */
class GetMcpServersUseCase(
    private val registry: McpRegistry
) {
    /**
     * Returns all MCP servers: presets merged with saved overrides.
     */
    suspend operator fun invoke(): List<McpServerConfig> = registry.getAll()
}
