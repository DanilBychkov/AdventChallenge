package org.bothubclient.domain.entity

import kotlinx.serialization.Serializable

@Serializable
enum class McpTransportType {
    STDIO,
    HTTP
}

@Serializable
enum class McpHealthStatus {
    UNKNOWN,
    ONLINE,
    OFFLINE,
    ERROR
}

@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val type: String,
    val description: String = "",
    val enabled: Boolean = false,
    val forceUsage: Boolean = false,
    val transportType: McpTransportType = McpTransportType.STDIO,
    val command: String? = null,
    val args: List<String>? = null,
    val env: Map<String, String>? = null,
    val url: String? = null,
    val headers: Map<String, String>? = null,
    val capabilities: List<String>? = null,
    val priority: Int = 50,
    val healthStatus: McpHealthStatus = McpHealthStatus.UNKNOWN,
    val lastHealthCheckAt: Long? = null
) {
    init {
        require(priority in 0..100) { "Priority must be in range 0..100, but was $priority" }
    }

    fun withEnabled(enabled: Boolean): McpServerConfig = copy(enabled = enabled)

    fun withHealthStatus(status: McpHealthStatus): McpServerConfig =
        copy(healthStatus = status, lastHealthCheckAt = System.currentTimeMillis())

    fun withPriority(newPriority: Int): McpServerConfig =
        copy(priority = newPriority.coerceIn(0, 100))
}
