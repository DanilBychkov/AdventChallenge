package org.bothubclient.infrastructure.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bothubclient.config.McpPresets
import org.bothubclient.domain.entity.McpServerConfig
import org.bothubclient.domain.repository.McpRegistry
import org.bothubclient.infrastructure.persistence.FileMcpSettingsStorage

/**
 * Default implementation of McpRegistry that merges presets with saved overrides.
 */
class DefaultMcpRegistry(
    private val storage: FileMcpSettingsStorage
) : McpRegistry {

    private val mutex = Mutex()

    /**
     * Returns all MCP servers: presets merged with saved overrides.
     * Merge strategy: For each preset id, if storage has a config with same id,
     * use stored one (user overrides); else use preset.
     */
    override suspend fun getAll(): List<McpServerConfig> {
        val presets = McpPresets.getAllPresets()
        val stored = storage.loadServers()
        val storedById = stored.associateBy { it.id }
        val presetIds = presets.map { it.id }.toSet()
        val mergedPresets = presets.map { preset -> storedById[preset.id] ?: preset }
        val customOnly = stored.filter { it.id !in presetIds }
        return mergedPresets + customOnly
    }

    /**
     * Returns a specific MCP server by id, or null if not found.
     */
    override suspend fun getById(id: String): McpServerConfig? {
        return getAll().find { it.id == id }
    }

    /**
     * Returns only enabled MCP servers (enabled == true).
     */
    override suspend fun getEnabled(): List<McpServerConfig> {
        return getAll().filter { it.enabled }
    }

    /**
     * Returns servers that are enabled AND have forceUsage == true.
     * Ordered by priority ascending (lower number = higher priority).
     */
    override suspend fun getForced(): List<McpServerConfig> {
        return getAll()
            .filter { it.enabled && it.forceUsage }
            .sortedBy { it.priority }
    }

    /**
     * Atomically reads, modifies, and writes MCP server configurations.
     * Acquires a lock, loads from storage, merges with presets, applies the block,
     * saves the result, and releases the lock.
     * 
     * @param block A suspend function that receives the merged list and returns a Pair of (modified list, result).
     * @return The result of the block operation.
     */
    override suspend fun <T> runAtomicUpdate(block: suspend (List<McpServerConfig>) -> Pair<List<McpServerConfig>, T>): T {
        return mutex.withLock {
            // Load from storage
            val stored = storage.loadServers()
            // Merge with presets (same logic as getAll)
            val presets = McpPresets.getAllPresets()
            val storedById = stored.associateBy { it.id }
            val presetIds = presets.map { it.id }.toSet()
            val mergedPresets = presets.map { preset -> storedById[preset.id] ?: preset }
            val customOnly = stored.filter { it.id !in presetIds }
            val merged = mergedPresets + customOnly
            // Apply the modification block
            val (newList, result) = block(merged)
            // Save the result
            storage.saveServers(newList)
            // Return the result
            result
        }
    }
}
