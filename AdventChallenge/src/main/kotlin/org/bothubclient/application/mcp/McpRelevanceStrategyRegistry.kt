package org.bothubclient.application.mcp

import org.bothubclient.domain.entity.McpServerConfig

interface McpRelevanceStrategyRegistry {
    fun registerForType(serverType: String, strategy: McpRelevanceStrategy)
    fun registerForServerId(serverId: String, strategy: McpRelevanceStrategy)
    fun getStrategy(server: McpServerConfig): McpRelevanceStrategy
}

class DefaultMcpRelevanceStrategyRegistry(
    private val fallbackStrategy: McpRelevanceStrategy = FallbackRelevanceStrategy()
) : McpRelevanceStrategyRegistry {

    private val strategiesByType = mutableMapOf<String, McpRelevanceStrategy>()
    private val strategiesByServerId = mutableMapOf<String, McpRelevanceStrategy>()

    override fun registerForType(serverType: String, strategy: McpRelevanceStrategy) {
        strategiesByType[serverType.lowercase()] = strategy
    }

    override fun registerForServerId(serverId: String, strategy: McpRelevanceStrategy) {
        strategiesByServerId[serverId.lowercase()] = strategy
    }

    override fun getStrategy(server: McpServerConfig): McpRelevanceStrategy {
        return strategiesByServerId[server.id.lowercase()]
            ?: strategiesByType[server.type.lowercase()]
            ?: fallbackStrategy
    }

    companion object {
        fun withDefaults(
            fallbackStrategy: McpRelevanceStrategy = FallbackRelevanceStrategy()
        ): DefaultMcpRelevanceStrategyRegistry {
            return DefaultMcpRelevanceStrategyRegistry(fallbackStrategy).apply {
                registerForType(CONTEXT7_SERVER_TYPE, Context7RelevanceStrategy())
            }
        }
    }
}
