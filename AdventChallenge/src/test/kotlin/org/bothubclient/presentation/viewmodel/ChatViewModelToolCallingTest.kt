package org.bothubclient.presentation.viewmodel

import kotlinx.coroutines.flow.MutableSharedFlow
import org.bothubclient.domain.entity.*
import org.bothubclient.infrastructure.agent.McpToolCallEvent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatViewModelToolCallingTest {

    @Test
    fun `handleToolCallEvent adds MCP message to messages`() {
        val event = McpToolCallEvent(
            serverId = "search-mcp",
            serverName = "Search MCP",
            toolName = "search",
            arguments = """{"query":"Python"}""",
            result = """{"title":"Python","extract":"Python is..."}""",
            durationMs = 150,
            isError = false
        )

        val info = McpToolCallInfo(
            toolName = event.toolName,
            serverName = event.serverName,
            arguments = event.arguments,
            durationMs = event.durationMs,
            isError = event.isError
        )

        val message = Message.mcpToolCall(
            serverName = event.serverName,
            toolName = event.toolName,
            content = event.result,
            info = info
        )

        assertEquals(MessageRole.SYSTEM, message.role)
        assertNotNull(message.mcpToolCall)
        assertEquals("search", message.mcpToolCall!!.toolName)
        assertEquals("Search MCP", message.mcpToolCall!!.serverName)
        assertEquals(150L, message.mcpToolCall!!.durationMs)
        assertEquals(false, message.mcpToolCall!!.isError)
    }

    @Test
    fun `MCP messages have correct McpToolCallInfo`() {
        val events = listOf(
            McpToolCallEvent("search-mcp", "Search MCP", "search", "{}", "result1", 100),
            McpToolCallEvent("summarize-mcp", "Summarize MCP", "summarize", "{}", "result2", 200),
            McpToolCallEvent("save-mcp", "Save MCP", "save-to-file", "{}", "result3", 300)
        )

        val messages = events.map { event ->
            val info = McpToolCallInfo(
                toolName = event.toolName,
                serverName = event.serverName,
                arguments = event.arguments,
                durationMs = event.durationMs,
                isError = event.isError
            )
            Message.mcpToolCall(event.serverName, event.toolName, event.result, info)
        }

        assertEquals(3, messages.size)
        assertEquals("search", messages[0].mcpToolCall!!.toolName)
        assertEquals("summarize", messages[1].mcpToolCall!!.toolName)
        assertEquals("save-to-file", messages[2].mcpToolCall!!.toolName)
        assertEquals(100L, messages[0].mcpToolCall!!.durationMs)
        assertEquals(200L, messages[1].mcpToolCall!!.durationMs)
        assertEquals(300L, messages[2].mcpToolCall!!.durationMs)
    }

    @Test
    fun `tool call error shows error message with isError true`() {
        val event = McpToolCallEvent(
            serverId = "search-mcp",
            serverName = "Search MCP",
            toolName = "search",
            arguments = """{"query":"test"}""",
            result = "callTool failed: timeout",
            durationMs = 5000,
            isError = true
        )

        val info = McpToolCallInfo(
            toolName = event.toolName,
            serverName = event.serverName,
            arguments = event.arguments,
            durationMs = event.durationMs,
            isError = event.isError
        )
        val message = Message.mcpToolCall(event.serverName, event.toolName, event.result, info)

        assertTrue(message.mcpToolCall!!.isError)
        assertTrue(message.content.contains("callTool failed"))
    }

    @Test
    fun `McpToolCallEvent can be created with default isError false`() {
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
    fun `MCP messages displayed with truncated content`() {
        val longContent = "a".repeat(1000)
        val displayContent = if (longContent.length > 500) longContent.take(500) + "..." else longContent
        assertEquals(503, displayContent.length)
        assertTrue(displayContent.endsWith("..."))
    }

    @Test
    fun `MCP messages appear as SYSTEM role`() {
        val info = McpToolCallInfo(
            toolName = "search",
            serverName = "Search MCP",
            arguments = "{}",
            durationMs = 50
        )
        val message = Message.mcpToolCall("Search MCP", "search", "result", info)
        assertEquals(MessageRole.SYSTEM, message.role)
    }

    @Test
    fun `regular message has null mcpToolCall`() {
        val userMsg = Message.user("hello")
        val assistantMsg = Message.assistant("world")
        val errorMsg = Message.error("oops")

        assertNull(userMsg.mcpToolCall)
        assertNull(assistantMsg.mcpToolCall)
        assertNull(errorMsg.mcpToolCall)
    }
}
