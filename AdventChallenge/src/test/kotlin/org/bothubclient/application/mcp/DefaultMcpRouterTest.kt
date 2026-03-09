package org.bothubclient.application.mcp

import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.entity.McpServerConfig
import org.bothubclient.domain.repository.McpRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultMcpRouterTest {

    @Test
    fun `selectForRequest returns forced and optional from registry`() = runTest {
        val context7 = McpServerConfig(
            id = "context7",
            name = "Context7",
            type = "documentation",
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
        val router = DefaultMcpRouter(registry)

        val result = router.selectForRequest("Need API docs and migration example")

        assertEquals(listOf("forced-top", "forced-later"), result.forcedServers.map { it.id })
        assertEquals(listOf("context7"), result.optionalServers.map { it.id })
        assertEquals("enabled_split_forced_optional", result.metadata["strategy"])
        assertEquals("true", result.metadata["context7Relevant"])
    }

    @Test
    fun `selectForRequest marks context7 as not relevant when no keywords`() = runTest {
        val registry = FakeMcpRegistry(enabled = emptyList(), forced = emptyList())
        val router = DefaultMcpRouter(registry)

        val result = router.selectForRequest("Summarize yesterday's meeting notes")

        assertTrue(result.forcedServers.isEmpty())
        assertTrue(result.optionalServers.isEmpty())
        assertEquals("false", result.metadata["context7Relevant"])
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
}
