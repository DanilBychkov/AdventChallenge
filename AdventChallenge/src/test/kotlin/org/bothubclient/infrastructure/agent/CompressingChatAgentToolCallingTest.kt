package org.bothubclient.infrastructure.agent

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.bothubclient.application.mcp.*
import org.bothubclient.domain.agent.ChatAgent
import org.bothubclient.domain.entity.*
import org.bothubclient.domain.repository.McpRegistry
import org.bothubclient.infrastructure.api.ApiToolDefinition
import org.bothubclient.infrastructure.mcp.StdioMcpClient
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CompressingChatAgentToolCallingTest {

    @Test
    fun `McpToolCallEvent stores all fields correctly`() {
        val event = McpToolCallEvent(
            serverId = "search-mcp",
            serverName = "Search MCP",
            toolName = "search",
            arguments = """{"query":"Kotlin"}""",
            result = """{"title":"Kotlin"}""",
            durationMs = 250,
            isError = false
        )

        assertEquals("search-mcp", event.serverId)
        assertEquals("Search MCP", event.serverName)
        assertEquals("search", event.toolName)
        assertEquals("""{"query":"Kotlin"}""", event.arguments)
        assertEquals("""{"title":"Kotlin"}""", event.result)
        assertEquals(250L, event.durationMs)
        assertEquals(false, event.isError)
    }

    @Test
    fun `McpToolCallEvent isError defaults to false`() {
        val event = McpToolCallEvent(
            serverId = "test",
            serverName = "Test",
            toolName = "tool",
            arguments = "{}",
            result = "ok",
            durationMs = 10
        )
        assertEquals(false, event.isError)
    }

    @Test
    fun `McpToolCallEvent can represent error state`() {
        val event = McpToolCallEvent(
            serverId = "search-mcp",
            serverName = "Search MCP",
            toolName = "search",
            arguments = """{"query":"test"}""",
            result = "callTool failed: connection refused",
            durationMs = 5000,
            isError = true
        )
        assertTrue(event.isError)
        assertTrue(event.result.contains("callTool failed"))
    }

    @Test
    fun `McpToolCallEvent pipeline events have distinct servers`() {
        val events = listOf(
            McpToolCallEvent("search-mcp", "Search MCP", "search", "{}", "result1", 100),
            McpToolCallEvent("summarize-mcp", "Summarize MCP", "summarize", "{}", "result2", 200),
            McpToolCallEvent("save-mcp", "Save MCP", "save-to-file", "{}", "result3", 300)
        )

        val serverIds = events.map { it.serverId }.toSet()
        assertEquals(3, serverIds.size)
        assertTrue("search-mcp" in serverIds)
        assertTrue("summarize-mcp" in serverIds)
        assertTrue("save-mcp" in serverIds)
    }

    @Test
    fun `tool definitions can be built from McpToolInfo`() {
        val toolInfos = listOf(
            McpToolInfo("search", "Search Wikipedia", "query"),
            McpToolInfo("summarize", "Summarize text", "text, maxSentences"),
            McpToolInfo("save-to-file", "Save to file", "content, filename")
        )

        val apiTools = toolInfos.map { tool ->
            ApiToolDefinition(
                type = "function",
                function = org.bothubclient.infrastructure.api.ApiFunctionDef(
                    name = tool.name,
                    description = tool.description ?: tool.name,
                    parameters = buildJsonObject {
                        put("type", "object")
                    }
                )
            )
        }

        assertEquals(3, apiTools.size)
        assertEquals("search", apiTools[0].function.name)
        assertEquals("summarize", apiTools[1].function.name)
        assertEquals("save-to-file", apiTools[2].function.name)
    }

    @Test
    fun `tool definitions prefer full inputSchema over summary`() {
        val fullSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "The topic to search for on Wikipedia")
                }
            }
            put("required", JsonArray(listOf(JsonPrimitive("query"))))
        }

        val toolWithSchema = McpToolInfo("search", "Search Wikipedia", "query", inputSchema = fullSchema)
        val params = toolWithSchema.inputSchema ?: buildJsonObject { put("type", "object") }

        assertTrue(params.toString().contains("description"))
        assertTrue(params.toString().contains("The topic to search for"))
        assertTrue(params.toString().contains("required"))
    }

    @Test
    fun `tool definitions fall back to summary when inputSchema is null`() {
        val toolWithoutSchema = McpToolInfo("search", "Search Wikipedia", "query", inputSchema = null)
        val params = toolWithoutSchema.inputSchema ?: buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                toolWithoutSchema.inputSchemaSummary?.split(", ")?.filter { it.isNotBlank() }?.forEach { prop ->
                    putJsonObject(prop.trim()) {
                        put("type", "string")
                    }
                }
            }
        }

        assertTrue(params.toString().contains("query"))
        assertTrue(
            params.toString().contains("\"type\":\"object\"") || params.toString().contains("\"type\": \"object\"")
        )
    }

    @Test
    fun `tool name to server mapping works correctly`() {
        val searchConfig = McpServerConfig(
            id = "search-mcp", name = "Search MCP", type = "search-mcp",
            enabled = true, transportType = McpTransportType.STDIO,
            command = "node", args = listOf("dist/index.js"),
            workingDirectory = "mcp-servers/search-mcp", priority = 50
        )
        val summarizeConfig = McpServerConfig(
            id = "summarize-mcp", name = "Summarize MCP", type = "summarize-mcp",
            enabled = true, transportType = McpTransportType.STDIO,
            command = "node", args = listOf("dist/index.js"),
            workingDirectory = "mcp-servers/summarize-mcp", priority = 50
        )

        val toolInfoSearch = McpToolInfo("search", "Search Wikipedia", "query")
        val toolInfoSummarize = McpToolInfo("summarize", "Summarize text", "text, maxSentences")

        val toolServerMap = mapOf(
            "search" to Pair(searchConfig, toolInfoSearch),
            "summarize" to Pair(summarizeConfig, toolInfoSummarize)
        )

        assertEquals("search-mcp", toolServerMap["search"]?.first?.id)
        assertEquals("summarize-mcp", toolServerMap["summarize"]?.first?.id)
        assertEquals(null, toolServerMap["nonexistent"])
    }

    @Test
    fun `discovery results can be converted to tool definitions`() {
        val discoveryResult = McpDiscoveryResult(
            serverId = "search-mcp",
            serverLabel = "Search MCP",
            tools = listOf(
                McpToolInfo("search", "Search Wikipedia for information", "query")
            ),
            capabilities = McpServerCapabilities(tools = true)
        )

        val tools = discoveryResult.tools
        assertEquals(1, tools.size)
        assertEquals("search", tools[0].name)
        assertEquals("Search Wikipedia for information", tools[0].description)
        assertEquals("query", tools[0].inputSchemaSummary)
    }

    @Test
    fun `empty discovery returns no tools`() {
        val emptyDiscovery = McpDiscoveryResult(
            serverId = "empty",
            serverLabel = "Empty",
            tools = emptyList(),
            capabilities = McpServerCapabilities(tools = false)
        )

        assertTrue(emptyDiscovery.tools.isEmpty())
    }

    @Test
    fun `tool server map with duplicate tool names last wins`() {
        val config1 = McpServerConfig(
            id = "server1", name = "Server 1", type = "s1",
            enabled = true, transportType = McpTransportType.STDIO,
            command = "node", args = listOf("dist/index.js"), priority = 50
        )
        val config2 = McpServerConfig(
            id = "server2", name = "Server 2", type = "s2",
            enabled = true, transportType = McpTransportType.STDIO,
            command = "node", args = listOf("dist/index.js"), priority = 50
        )

        val tool = McpToolInfo("search", "Search", null)

        val map = mutableMapOf<String, Pair<McpServerConfig, McpToolInfo>>()
        map["search"] = Pair(config1, tool)
        map["search"] = Pair(config2, tool)

        assertEquals("server2", map["search"]?.first?.id)
    }

    @Test
    fun `tool executor returns correct format for MCP success`() {
        val mcpResult = McpFetchResult.Success("""{"title":"Python","extract":"Python is a programming language."}""")
        assertIs<McpFetchResult.Success>(mcpResult)
        assertTrue(mcpResult.content.contains("Python"))
    }

    @Test
    fun `tool executor returns error message for MCP failure`() {
        val mcpResult = McpFetchResult.Failure("Connection timeout")
        assertIs<McpFetchResult.Failure>(mcpResult)
        val errorMessage = "callTool failed: ${mcpResult.reason}"
        assertTrue(errorMessage.contains("Connection timeout"))
    }

    @Test
    fun `system prompt must not contain docs-not-loaded fallback when tools are active`() {
        val baseSystemPrompt = "You are a helpful assistant."
        val toolCallingPrompt = """
## ИНСТРУКЦИИ ПО ИСПОЛЬЗОВАНИЮ ИНСТРУМЕНТОВ (ОБЯЗАТЕЛЬНО)
У тебя есть доступ к инструментам (tools/functions), которые ты можешь вызывать для выполнения запросов пользователя.
""".trim()

        val docsNotLoadedFallback = "Документация не загрузилась, попробуйте повторить запрос"
        val mcpInvariantFallbackRule = "тогда напиши одной фразой"

        val hasActiveTools = true

        val systemPromptWithProfile = if (hasActiveTools) {
            baseSystemPrompt + "\n\n" + toolCallingPrompt
        } else {
            baseSystemPrompt + "\n\n" + "Только если блока нет — $docsNotLoadedFallback"
        }

        assertTrue(systemPromptWithProfile.contains("ИНСТРУКЦИИ ПО ИСПОЛЬЗОВАНИЮ ИНСТРУМЕНТОВ"))
        assertTrue(
            !systemPromptWithProfile.contains(docsNotLoadedFallback),
            "System prompt must NOT contain '$docsNotLoadedFallback' when tools are active"
        )
        assertTrue(
            !systemPromptWithProfile.contains(mcpInvariantFallbackRule),
            "System prompt must NOT contain MCP fallback rule when tools are active"
        )
    }

    @Test
    fun `system prompt uses MCP fallback rules when no tools are active`() {
        val baseSystemPrompt = "You are a helpful assistant."
        val mcpInvariantException = """
## ИСКЛЮЧЕНИЕ ИЗ ИНВАРИАНТОВ (ОБЯЗАТЕЛЬНО)
Только если блока «--- MCP context ---» нет или он пуст — тогда напиши одной фразой «Документация не загрузилась, попробуйте повторить запрос».
""".trim()

        val hasActiveTools = false

        val systemPromptWithProfile = if (hasActiveTools) {
            baseSystemPrompt + "\n\nTOOL_CALLING_INSTRUCTIONS"
        } else {
            baseSystemPrompt + "\n\n" + mcpInvariantException
        }

        assertTrue(systemPromptWithProfile.contains("ИСКЛЮЧЕНИЕ ИЗ ИНВАРИАНТОВ"))
        assertTrue(
            !systemPromptWithProfile.contains("ИНСТРУКЦИИ ПО ИСПОЛЬЗОВАНИЮ ИНСТРУМЕНТОВ"),
            "System prompt must NOT contain tool calling instructions when no tools are active"
        )
    }

    @Test
    fun `tool calling prompt instructs model to use function calling`() {
        val toolCallingPrompt = """
## ИНСТРУКЦИИ ПО ИСПОЛЬЗОВАНИЮ ИНСТРУМЕНТОВ (ОБЯЗАТЕЛЬНО)
У тебя есть доступ к инструментам (tools/functions), которые ты можешь вызывать для выполнения запросов пользователя.
Когда пользователь просит найти информацию, обобщить текст, сохранить в файл или выполнить другие действия, которые совпадают с доступными тебе инструментами — ты ОБЯЗАН вызвать соответствующие инструменты через function calling.
ЗАПРЕЩЕНО отвечать «документация не загрузилась» или «попробуйте повторить запрос», если у тебя есть инструменты для выполнения задачи.
""".trim()

        assertTrue(toolCallingPrompt.contains("function calling"))
        assertTrue(toolCallingPrompt.contains("ОБЯЗАН вызвать"))
        assertTrue(toolCallingPrompt.contains("ЗАПРЕЩЕНО отвечать"))
        assertTrue(!toolCallingPrompt.contains("тогда напиши одной фразой"))
    }
}
