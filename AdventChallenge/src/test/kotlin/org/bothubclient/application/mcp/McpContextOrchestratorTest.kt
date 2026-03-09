package org.bothubclient.application.mcp

import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.entity.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
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

        assertEquals("forced-context-a\nforced-context-b\noptional-context", result)
        assertEquals(
            listOf("forced-a", "forced-b", "context7"),
            client.calls.map { it.id }
        )
    }

    @Test
    fun `fetchEnrichedContext skips optional when context7 is not relevant`() = runTest {
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
                results = mapOf(forced.id to McpFetchResult.Success("forced-only"))
            )
        val orchestrator = McpContextOrchestrator(mcpRouter = router, mcpClient = client)

        val result =
            orchestrator.fetchEnrichedContext(
                userMessage = "Summarize my notes from yesterday",
                sessionId = "s2"
            )

        assertEquals("forced-only", result)
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

        assertEquals("", result)
        assertEquals(listOf("forced-a", "forced-b", "context7"), client.calls.map { it.id })
    }
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
    private val results: Map<String, McpFetchResult>
) : McpClient {
    val calls = mutableListOf<McpServerConfig>()

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
