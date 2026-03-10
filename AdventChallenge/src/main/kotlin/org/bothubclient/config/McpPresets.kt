package org.bothubclient.config

import org.bothubclient.domain.entity.McpHealthStatus
import org.bothubclient.domain.entity.McpServerConfig
import org.bothubclient.domain.entity.McpTransportType

object McpPresets {
    /**
     * Context7 MCP preset — matches official installation and API:
     * - Installation: https://github.com/upstash/context7#installation
     * - API (libs/search + context): https://context7.com/docs/api-guide
     * The MCP server uses GET /api/v2/libs/search (libraryName + query) and GET /api/v2/context (libraryId + query).
     * For auth and higher rate limits: get API key at context7.com/dashboard, then in MCP settings add env:
     * CONTEXT7_API_KEY = "ctx7sk_..." (Bearer token for Authorization header).
     * Or args: ["-y", "@upstash/context7-mcp", "--api-key", "YOUR_KEY"].
     */
    val CONTEXT7_PRESET = McpServerConfig(
        id = "context7",
        name = "Context7",
        type = "context7",
        description = "Up-to-date documentation and code examples (context7.com API: libs/search + context)",
        enabled = false,
        forceUsage = false,
        transportType = McpTransportType.STDIO,
        command = "npx",
        args = listOf("-y", "@upstash/context7-mcp"),
        env = null,
        url = null,
        headers = null,
        capabilities = null,
        priority = 50,
        healthStatus = McpHealthStatus.UNKNOWN,
        lastHealthCheckAt = null
    )

    /**
     * Bored API MCP preset — local MCP server for random activity suggestions.
     * Located in mcp-servers/bored-api-mcp folder.
     * The workingDirectory is set to the bored-api-mcp folder relative to repo root.
     * Requires: node dist/index.js to be runnable from that directory.
     */
    val BORED_API_PRESET = McpServerConfig(
        id = "bored-api",
        name = "Bored API",
        type = "bored-api",
        description = "Random activity suggestions (local MCP server in mcp-servers/bored-api-mcp)",
        enabled = false,
        forceUsage = false,
        transportType = McpTransportType.STDIO,
        command = "node",
        args = listOf("dist/index.js"),
        env = null,
        url = null,
        headers = null,
        capabilities = null,
        priority = 50,
        healthStatus = McpHealthStatus.UNKNOWN,
        lastHealthCheckAt = null,
        workingDirectory = "mcp-servers/bored-api-mcp"
    )

    fun getAllPresets(): List<McpServerConfig> = listOf(CONTEXT7_PRESET, BORED_API_PRESET)

    fun getPresetById(id: String): McpServerConfig? = getAllPresets().find { it.id == id }
}
