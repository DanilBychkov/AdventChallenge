package org.bothubclient.application.mcp

import org.bothubclient.domain.entity.McpServerConfig

/** Result of MCP discovery: tools, resources, and prompts the server exposes (aligned with MCP spec). */
data class McpDiscoveryResult(
    val serverId: String,
    val serverLabel: String,
    val tools: List<McpToolInfo>,
    val resources: List<McpResourceInfo> = emptyList(),
    val prompts: List<McpPromptInfo> = emptyList(),
    val capabilities: McpServerCapabilities = McpServerCapabilities()
)

/** One MCP tool: name, description, input schema for model/harness. */
data class McpToolInfo(
    val name: String,
    val description: String?,
    val inputSchemaSummary: String?
)

/** One MCP resource: URI and metadata (data the agent can read). */
data class McpResourceInfo(
    val uri: String,
    val name: String?,
    val description: String?,
    val mimeType: String?
)

/** One MCP prompt: template the user or agent can invoke (e.g. slash commands). */
data class McpPromptInfo(
    val name: String,
    val description: String?
)

/** Capabilities announced by server in initialize response. */
data class McpServerCapabilities(
    val tools: Boolean = false,
    val resources: Boolean = false,
    val prompts: Boolean = false
)

interface McpClient {
    /** Connects to server, runs initialize, returns tools/list (and optionally resources/list, prompts/list). */
    suspend fun discover(server: McpServerConfig): McpDiscoveryResult?

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
