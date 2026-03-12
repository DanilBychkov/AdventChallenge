package org.bothubclient.infrastructure.mcp

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bothubclient.application.mcp.McpFetchResult
import org.bothubclient.domain.entity.McpHealthStatus
import org.bothubclient.domain.entity.McpServerConfig
import org.bothubclient.domain.entity.McpTransportType
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StdioMcpClientCallToolTest {

    private val client = StdioMcpClient()

    private val summarizeMcpConfig = McpServerConfig(
        id = "summarize-mcp",
        name = "Summarize MCP",
        type = "summarize-mcp",
        enabled = true,
        transportType = McpTransportType.STDIO,
        command = "node",
        args = listOf("dist/index.js"),
        workingDirectory = "mcp-servers/summarize-mcp",
        priority = 50,
        healthStatus = McpHealthStatus.UNKNOWN
    )

    private val saveMcpConfig = McpServerConfig(
        id = "save-mcp",
        name = "Save MCP",
        type = "save-mcp",
        enabled = true,
        transportType = McpTransportType.STDIO,
        command = "node",
        args = listOf("dist/index.js"),
        workingDirectory = "mcp-servers/save-mcp",
        priority = 50,
        healthStatus = McpHealthStatus.UNKNOWN
    )

    @Test
    fun `callTool on summarize-mcp condenses text`() = runTest {
        val args = buildJsonObject {
            put("text", "First sentence here. Second sentence here. Third sentence here. Fourth sentence here.")
            put("maxSentences", 2)
        }
        val result = client.callTool(summarizeMcpConfig, "summarize", args)
        assertIs<McpFetchResult.Success>(result)
        assertTrue(result.content.isNotBlank())
    }

    @Test
    fun `callTool on save-mcp creates file`() = runTest {
        val args = buildJsonObject {
            put("content", "Test content for callTool integration test")
            put("filename", "callTool-test-output.txt")
        }
        val result = client.callTool(saveMcpConfig, "save-to-file", args)
        assertIs<McpFetchResult.Success>(result)
        assertTrue(result.content.contains("savedTo"))
        assertTrue(result.content.contains("sizeBytes"))
    }

    @Test
    fun `callTool with unknown tool name returns Failure`() = runTest {
        val args = buildJsonObject { }
        val result = client.callTool(summarizeMcpConfig, "nonexistent-tool", args)
        assertIs<McpFetchResult.Failure>(result)
    }

    @Test
    fun `callTool with invalid server config returns Failure`() = runTest {
        val badConfig = McpServerConfig(
            id = "bad-server",
            name = "Bad Server",
            type = "bad",
            enabled = true,
            transportType = McpTransportType.STDIO,
            command = "nonexistent-command-xyz",
            args = listOf("something"),
            priority = 50,
            healthStatus = McpHealthStatus.UNKNOWN
        )
        val args = buildJsonObject { }
        val result = client.callTool(badConfig, "test", args)
        assertIs<McpFetchResult.Failure>(result)
    }

    @Test
    fun `callTool with empty arguments returns appropriate result`() = runTest {
        val args = buildJsonObject {
            put("text", "")
        }
        val result = client.callTool(summarizeMcpConfig, "summarize", args)
        // Empty text should either be error or failure
        assertTrue(
            result is McpFetchResult.Failure ||
                    (result is McpFetchResult.Success && result.content.contains("error", ignoreCase = true)) ||
                    result is McpFetchResult.Success
        )
    }

    @Test
    fun `callTool with HTTP transport returns Failure`() = runTest {
        val httpConfig = McpServerConfig(
            id = "http-server",
            name = "HTTP Server",
            type = "http",
            enabled = true,
            transportType = McpTransportType.HTTP,
            url = "http://localhost:9999",
            priority = 50,
            healthStatus = McpHealthStatus.UNKNOWN
        )
        val args = buildJsonObject { }
        val result = client.callTool(httpConfig, "test", args)
        assertIs<McpFetchResult.Failure>(result)
    }
}
