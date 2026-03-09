package org.bothubclient.config

import org.bothubclient.domain.entity.McpHealthStatus
import org.bothubclient.domain.entity.McpServerConfig
import org.bothubclient.domain.entity.McpTransportType

object McpPresets {
    val CONTEXT7_PRESET = McpServerConfig(
        id = "context7",
        name = "Context7",
        type = "context7",
        description = "Up-to-date documentation and code examples",
        enabled = false,
        forceUsage = false,
        transportType = McpTransportType.STDIO,
        command = "npx",
        args = listOf("-y", "@upstash/context7-mcp@latest"),
        env = mapOf("DEFAULT_MINIMUM_TOKENS" to "10000"),
        url = null,
        headers = null,
        capabilities = null,
        priority = 50,
        healthStatus = McpHealthStatus.UNKNOWN,
        lastHealthCheckAt = null
    )

    fun getAllPresets(): List<McpServerConfig> = listOf(CONTEXT7_PRESET)

    fun getPresetById(id: String): McpServerConfig? = getAllPresets().find { it.id == id }
}
