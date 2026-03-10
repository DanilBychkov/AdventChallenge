package org.bothubclient.application.mcp

import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.entity.McpServerConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class McpContextOrchestratorTest {

    @Test
    fun `fetchEnrichedContext merges forced and first successful optional`() = runTest {
        val forcedA = server(id = "forced-a", type = "search", forceUsage = true)
        val forcedB = server(id = "forced-b", type = "search", forceUsage = true)
        val optionalA = server(id = "context7", type = "context7")
        val optionalB = server(id = "optional-backup", type = "context7")

        val router =
            FakeMcpRouter(
                McpSelectionResult(
                    forcedServers = listOf(forcedA, forcedB),
                    optionalServers = listOf(optionalA, optionalB)
                )
            )
        val client =
            FakeMcpClient(
                results =
                    mapOf(
                        forcedA.id to McpFetchResult.Success("forced-context-a"),
                        forcedB.id to McpFetchResult.Success("forced-context-b"),
                        optionalA.id to McpFetchResult.Success("optional-context")
                    )
            )
        val orchestrator = McpContextOrchestrator(mcpRouter = router, mcpClient = client)

        val result =
            orchestrator.fetchEnrichedContext(
                userMessage = "Need API docs and migration examples",
                sessionId = "s1"
            )

        assertEquals("forced-context-a\nforced-context-b\noptional-context", result.content)
        assertEquals(
            listOf("forced-a", "forced-b", "context7"),
            client.calls.map { it.id }
        )
    }

    @Test
    fun `fetchEnrichedContext skips optional when router returns empty optionalServers`() = runTest {
        // This test verifies that the orchestrator trusts the router's selection.
        // The router (not the orchestrator) is responsible for filtering optional servers by relevance.
        val forced = server(id = "forced", type = "search", forceUsage = true)
        // Router returns empty optionalServers (e.g., because message is not relevant)
        val router =
            FakeMcpRouter(
                McpSelectionResult(
                    forcedServers = listOf(forced),
                    optionalServers = emptyList()
                )
            )
        val client =
            FakeMcpClient(
                results = mapOf(forced.id to McpFetchResult.Success("forced-only"))
            )
        val orchestrator = McpContextOrchestrator(mcpRouter = router, mcpClient = client)

        val result =
            orchestrator.fetchEnrichedContext(
                userMessage = "Summarize my notes from yesterday",
                sessionId = "s2"
            )

        assertEquals("forced-only", result.content)
        assertEquals(listOf("forced"), client.calls.map { it.id })
    }

    @Test
    fun `fetchEnrichedContext continues on failures and returns empty when none succeed`() = runTest {
        val forcedA = server(id = "forced-a", type = "search", forceUsage = true)
        val forcedB = server(id = "forced-b", type = "search", forceUsage = true)
        val optional = server(id = "context7", type = "context7")

        val router =
            FakeMcpRouter(
                McpSelectionResult(
                    forcedServers = listOf(forcedA, forcedB),
                    optionalServers = listOf(optional)
                )
            )
        val client =
            FakeMcpClient(
                results =
                    mapOf(
                        forcedA.id to McpFetchResult.Failure("timeout"),
                        forcedB.id to McpFetchResult.Failure("server unavailable"),
                        optional.id to McpFetchResult.Failure("no content")
                    )
            )
        val orchestrator = McpContextOrchestrator(mcpRouter = router, mcpClient = client)

        val result =
            orchestrator.fetchEnrichedContext(
                userMessage = "Show API docs for this SDK",
                sessionId = "s3"
            )

        assertEquals("", result.content)
        assertEquals(listOf("forced-a", "forced-b", "context7"), client.calls.map { it.id })
        // lastError is last failure reason when content is empty
        assertEquals("no content", result.lastError)
    }

    @Test
    fun `fetchEnrichedContext sets lastError to last failure when only optional fails`() = runTest {
        val forced = server(id = "forced", type = "search", forceUsage = true)
        val optional = server(id = "context7", type = "context7")

        val router =
            FakeMcpRouter(
                McpSelectionResult(
                    forcedServers = listOf(forced),
                    optionalServers = listOf(optional)
                )
            )
        val client =
            FakeMcpClient(
                results =
                    mapOf(
                        forced.id to McpFetchResult.Success(""),
                        optional.id to McpFetchResult.Failure("resolve-library-id returned no ID")
                    )
            )
        val orchestrator = McpContextOrchestrator(mcpRouter = router, mcpClient = client)

        val result =
            orchestrator.fetchEnrichedContext(
                userMessage = "Show kotlinx.coroutines docs",
                sessionId = "s4"
            )

        assertEquals("", result.content)
        assertEquals("resolve-library-id returned no ID", result.lastError)
    }

    @Test
    fun `fetchEnrichedContext has lastError null when content is not empty`() = runTest {
        val forced = server(id = "forced", type = "search", forceUsage = true)
        val router = FakeMcpRouter(McpSelectionResult(forcedServers = listOf(forced), optionalServers = emptyList()))
        val client = FakeMcpClient(results = mapOf(forced.id to McpFetchResult.Success("some-docs")))
        val orchestrator = McpContextOrchestrator(mcpRouter = router, mcpClient = client)

        val result = orchestrator.fetchEnrichedContext(userMessage = "docs", sessionId = null)

        assertEquals("some-docs", result.content)
        assertNull(result.lastError)
    }

    @Test
    fun `fetchEnrichedContext with exact user query Посмотри документацию по Kotlinx coroutines returns content when Context7 succeeds`() =
        runTest {
            val context7Server = server(id = "context7", type = "context7")
            val router =
                FakeMcpRouter(
                    McpSelectionResult(
                        forcedServers = emptyList(),
                        optionalServers = listOf(context7Server)
                    )
                )
            val userQuery = "Посмотри документацию по Kotlinx.coroutines"
            val client =
                FakeMcpClient(
                    results = mapOf(
                        context7Server.id to McpFetchResult.Success("# Kotlinx.Coroutines\n\nCoroutines documentation...")
                    )
                )
            val orchestrator = McpContextOrchestrator(mcpRouter = router, mcpClient = client)

            val result = orchestrator.fetchEnrichedContext(userMessage = userQuery, sessionId = "ctx7-test")

            assertTrue(result.content.isNotBlank())
            assertTrue(result.content.contains("Coroutines"))
            assertNull(result.lastError)
            assertEquals(listOf("context7"), client.calls.map { it.id })
        }

    @Test
    fun `fetchEnrichedContext propagates exception when client throws at runtime`() = runTest {
        val context7Server = server(id = "context7", type = "context7")
        val router =
            FakeMcpRouter(
                McpSelectionResult(
                    forcedServers = emptyList(),
                    optionalServers = listOf(context7Server)
                )
            )
        val throwingClient = ThrowingMcpClient("Process timeout")
        val orchestrator = McpContextOrchestrator(mcpRouter = router, mcpClient = throwingClient)

        val ex =
            kotlin.runCatching {
                orchestrator.fetchEnrichedContext(
                    userMessage = "Посмотри документацию Kotlinx.coroutines",
                    sessionId = "test"
                )
            }.exceptionOrNull()

        assertNotNull(ex)
        assertTrue(ex!!.message!!.contains("Process timeout"))
    }
}

private class ThrowingMcpClient(private val message: String) : McpClient {
    override suspend fun discover(server: McpServerConfig): McpDiscoveryResult? =
        McpDiscoveryResult(serverId = server.id, serverLabel = server.name, tools = emptyList())

    override suspend fun fetchContext(server: McpServerConfig, query: String): McpFetchResult =
        throw RuntimeException(message)

    override suspend fun checkHealth(server: McpServerConfig): McpHealthResult = McpHealthResult.Online
}

private class FakeMcpRouter(
    private val selection: McpSelectionResult
) : McpRouter {
    override suspend fun selectForRequest(
        userMessage: String,
        context: McpRequestContext?
    ): McpSelectionResult = selection
}

private class FakeMcpClient(
    private val results: Map<String, McpFetchResult>,
    private val discoveryResults: Map<String, McpDiscoveryResult> = emptyMap()
) : McpClient {
    val calls = mutableListOf<McpServerConfig>()

    override suspend fun discover(server: McpServerConfig): McpDiscoveryResult? =
        discoveryResults[server.id] ?: McpDiscoveryResult(
            serverId = server.id,
            serverLabel = server.name,
            tools = emptyList()
        )

    override suspend fun fetchContext(server: McpServerConfig, query: String): McpFetchResult {
        calls += server
        return results[server.id] ?: McpFetchResult.Failure("missing fake result")
    }

    override suspend fun checkHealth(server: McpServerConfig): McpHealthResult {
        return McpHealthResult.Online
    }
}

private fun server(
    id: String,
    type: String,
    forceUsage: Boolean = false
): McpServerConfig =
    McpServerConfig(
        id = id,
        name = id,
        type = type,
        enabled = true,
        forceUsage = forceUsage
    )
