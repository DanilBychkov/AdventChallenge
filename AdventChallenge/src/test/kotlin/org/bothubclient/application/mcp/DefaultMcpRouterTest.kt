package org.bothubclient.application.mcp

import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.entity.McpServerConfig
import org.bothubclient.domain.repository.McpRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultMcpRouterTest {

    @Test
    fun `selectForRequest returns forced and optional from registry filtered by relevance`() = runTest {
        val context7 = McpServerConfig(
            id = "context7",
            name = "Context7",
            type = "context7",
            enabled = true,
            forceUsage = false,
            priority = 20
        )
        val forcedHighPriority = McpServerConfig(
            id = "forced-top",
            name = "Forced Top",
            type = "search",
            enabled = true,
            forceUsage = true,
            priority = 10
        )
        val forcedLowPriority = McpServerConfig(
            id = "forced-later",
            name = "Forced Later",
            type = "search",
            enabled = true,
            forceUsage = true,
            priority = 30
        )

        val registry = FakeMcpRegistry(
            enabled = listOf(context7, forcedLowPriority, forcedHighPriority),
            forced = listOf(forcedHighPriority, forcedLowPriority)
        )
        val relevanceRegistry = DefaultMcpRelevanceStrategyRegistry.withDefaults()
        val router = DefaultMcpRouter(registry, relevanceRegistry)

        val result = router.selectForRequest("Need API docs and migration example")

        assertEquals(listOf("forced-top", "forced-later"), result.forcedServers.map { it.id })
        assertEquals(listOf("context7"), result.optionalServers.map { it.id })
        assertEquals("enabled_split_forced_optional_relevance", result.metadata["strategy"])
        assertEquals("context7", result.metadata["optionalPassed"])
        assertTrue(result.metadata["optionalFiltered"]?.isEmpty() == true)
    }

    @Test
    fun `selectForRequest filters optional servers by server-specific strategy`() = runTest {
        val context7Server = McpServerConfig(
            id = "context7",
            name = "Context7",
            type = "context7",
            enabled = true,
            forceUsage = false
        )
        val unknownServer = McpServerConfig(
            id = "unknown-server",
            name = "Unknown",
            type = "unknown-type",
            enabled = true,
            forceUsage = false
        )

        val registry = FakeMcpRegistry(
            enabled = listOf(context7Server, unknownServer),
            forced = emptyList()
        )
        // Use registry with defaults: context7 has strategy, unknown falls back to FallbackRelevanceStrategy(default=false)
        val relevanceRegistry = DefaultMcpRelevanceStrategyRegistry.withDefaults()
        val router = DefaultMcpRouter(registry, relevanceRegistry)

        // Message with documentation keyword - context7 should pass, unknown should be filtered
        val result = router.selectForRequest("Show me API documentation")

        assertEquals(listOf("context7"), result.optionalServers.map { it.id })
        assertEquals("context7", result.metadata["optionalPassed"])
        assertTrue(result.metadata["optionalFiltered"]?.contains("unknown-server") == true)
    }

    @Test
    fun `forced servers are independent of relevance strategy`() = runTest {
        val forcedContext7 = McpServerConfig(
            id = "context7-forced",
            name = "Context7 Forced",
            type = "context7",
            enabled = true,
            forceUsage = true,
            priority = 10
        )

        val registry = FakeMcpRegistry(
            enabled = listOf(forcedContext7),
            forced = listOf(forcedContext7)
        )
        val relevanceRegistry = DefaultMcpRelevanceStrategyRegistry.withDefaults()
        val router = DefaultMcpRouter(registry, relevanceRegistry)

        // Message without any documentation keywords - forced server should still be included
        val result = router.selectForRequest("Summarize yesterday's meeting notes")

        assertEquals(listOf("context7-forced"), result.forcedServers.map { it.id })
        assertTrue(result.optionalServers.isEmpty())
    }

    @Test
    fun `unknown server type with fallback false excluded from optional`() = runTest {
        val unknownServer = McpServerConfig(
            id = "unknown-server",
            name = "Unknown Server",
            type = "unknown-type",
            enabled = true,
            forceUsage = false
        )

        val registry = FakeMcpRegistry(
            enabled = listOf(unknownServer),
            forced = emptyList()
        )
        // Default fallback is false, so unknown server types are not relevant
        val relevanceRegistry = DefaultMcpRelevanceStrategyRegistry.withDefaults()
        val router = DefaultMcpRouter(registry, relevanceRegistry)

        val result = router.selectForRequest("Any message content here")

        assertTrue(result.optionalServers.isEmpty())
        assertTrue(result.metadata["optionalPassed"]?.isEmpty() == true)
        assertTrue(result.metadata["optionalFiltered"]?.contains("unknown-server") == true)
    }

    @Test
    fun `selectForRequest includes bored-api server when message contains activity keyword`() = runTest {
        val boredApiServer = McpServerConfig(
            id = "bored-api",
            name = "Bored API",
            type = "bored-api",
            enabled = true,
            forceUsage = false
        )
        val context7Server = McpServerConfig(
            id = "context7",
            name = "Context7",
            type = "context7",
            enabled = true,
            forceUsage = false
        )

        val registry = FakeMcpRegistry(
            enabled = listOf(boredApiServer, context7Server),
            forced = emptyList()
        )
        // Use registry with defaults: bored-api has BoredApiRelevanceStrategy registered
        val relevanceRegistry = DefaultMcpRelevanceStrategyRegistry.withDefaults()
        val router = DefaultMcpRouter(registry, relevanceRegistry)

        // Message with bored/activity keywords - bored-api should pass
        val result = router.selectForRequest("I'm bored, give me an activity idea")

        assertEquals(listOf("bored-api"), result.optionalServers.map { it.id })
        assertEquals("bored-api", result.metadata["optionalPassed"])
        assertTrue(result.metadata["optionalFiltered"]?.contains("context7") == true)
    }

    @Test
    fun `selectForRequest with custom relevance registry uses server-specific strategies`() = runTest {
        val serverA = McpServerConfig(
            id = "server-a",
            name = "Server A",
            type = "type-a",
            enabled = true,
            forceUsage = false
        )
        val serverB = McpServerConfig(
            id = "server-b",
            name = "Server B",
            type = "type-b",
            enabled = true,
            forceUsage = false
        )

        val registry = FakeMcpRegistry(
            enabled = listOf(serverA, serverB),
            forced = emptyList()
        )

        // Custom registry: type-a always relevant, type-b never relevant
        val relevanceRegistry = DefaultMcpRelevanceStrategyRegistry(
            fallbackStrategy = FallbackRelevanceStrategy(defaultRelevant = false)
        ).apply {
            registerForType("type-a", AlwaysRelevantStrategy())
            registerForType("type-b", NeverRelevantStrategy())
        }
        val router = DefaultMcpRouter(registry, relevanceRegistry)

        val result = router.selectForRequest("Any message")

        assertEquals(listOf("server-a"), result.optionalServers.map { it.id })
        assertEquals("server-a", result.metadata["optionalPassed"])
        assertTrue(result.metadata["optionalFiltered"]?.contains("server-b") == true)
    }
}

private class FakeMcpRegistry(
    private val enabled: List<McpServerConfig>,
    private val forced: List<McpServerConfig>
) : McpRegistry {
    override suspend fun getAll(): List<McpServerConfig> = enabled

    override suspend fun getById(id: String): McpServerConfig? =
        enabled.find { it.id == id } ?: forced.find { it.id == id }

    override suspend fun getEnabled(): List<McpServerConfig> = enabled

    override suspend fun getForced(): List<McpServerConfig> = forced

    override suspend fun <T> runAtomicUpdate(block: suspend (List<McpServerConfig>) -> Pair<List<McpServerConfig>, T>): T =
        block(enabled).let { (_, result) -> result }
}

// Test helper strategies
private class AlwaysRelevantStrategy : McpRelevanceStrategy {
    override fun isRelevant(
        server: McpServerConfig,
        userMessage: String,
        context: McpRequestContext?
    ): McpRelevanceResult =
        McpRelevanceResult(relevant = true, reason = "always_relevant")
}

private class NeverRelevantStrategy : McpRelevanceStrategy {
    override fun isRelevant(
        server: McpServerConfig,
        userMessage: String,
        context: McpRequestContext?
    ): McpRelevanceResult =
        McpRelevanceResult(relevant = false, reason = "never_relevant")
}
