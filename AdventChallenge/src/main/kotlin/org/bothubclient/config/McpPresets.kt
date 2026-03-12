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
     *
     * LOCATION: mcp-servers/bored-api-mcp folder.
     *
     * LAUNCH STRATEGY (production mode):
     * - Command: node dist/index.js
     * - Requires pre-build: npm run build (or mcp-use build) in mcp-servers/bored-api-mcp
     * - Build output: dist/index.js (compiled JavaScript from index.ts)
     *
     * WORKING DIRECTORY:
     * - workingDirectory = "mcp-servers/bored-api-mcp"
     * - Resolved via java.io.File(wd).absoluteFile in StdioMcpClient
     * - Relative to process cwd (typically repo root: AdventChallenge/)
     *
     * HEALTHCHECK BEHAVIOR:
     * - If dist/index.js is missing (not built), healthcheck will fail with error:
     *   "Unexpected healthcheck error: Cannot run program "node" (in directory "...")"
     *   or similar file not found error from the OS.
     * - Build prerequisite: cd mcp-servers/bored-api-mcp && npm install && npm run build
     *
     * ALTERNATIVE (dev mode, not used in production):
     * - Could use: npx tsx index.ts (no pre-build required)
     * - Not chosen for production to avoid npx overhead on each healthcheck
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

    val SEARCH_MCP_PRESET = McpServerConfig(
        id = "search-mcp",
        name = "Search MCP",
        type = "search-mcp",
        description = "Search Wikipedia for information on any topic",
        enabled = true,
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
        workingDirectory = "mcp-servers/search-mcp"
    )

    val SUMMARIZE_MCP_PRESET = McpServerConfig(
        id = "summarize-mcp",
        name = "Summarize MCP",
        type = "summarize-mcp",
        description = "Summarize text by extracting key sentences",
        enabled = true,
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
        workingDirectory = "mcp-servers/summarize-mcp"
    )

    val SAVE_MCP_PRESET = McpServerConfig(
        id = "save-mcp",
        name = "Save MCP",
        type = "save-mcp",
        description = "Save text content to a local file",
        enabled = true,
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
        workingDirectory = "mcp-servers/save-mcp"
    )

    fun getAllPresets(): List<McpServerConfig> = listOf(
        CONTEXT7_PRESET, BORED_API_PRESET,
        SEARCH_MCP_PRESET, SUMMARIZE_MCP_PRESET, SAVE_MCP_PRESET
    )

    fun getPresetById(id: String): McpServerConfig? = getAllPresets().find { it.id == id }
}
