package org.bothubclient.domain.repository

import org.bothubclient.domain.entity.McpServerConfig

/**
 * Registry interface for MCP server configurations.
 * Provides merged view of presets with user overrides.
 */
interface McpRegistry {
    /**
     * Returns all MCP servers: presets merged with saved overrides.
     * For each preset id, if storage has a config, use stored one; else use preset.
     */
    suspend fun getAll(): List<McpServerConfig>

    /**
     * Returns a specific MCP server by id, or null if not found.
     */
    suspend fun getById(id: String): McpServerConfig?

    /**
     * Returns only enabled MCP servers (enabled == true).
     */
    suspend fun getEnabled(): List<McpServerConfig>

    /**
     * Returns servers that are enabled AND have forceUsage == true.
     * Ordered by priority ascending (lower number = higher priority).
     */
    suspend fun getForced(): List<McpServerConfig>
}
