package org.bothubclient.domain.entity

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class McpToolCallMessageTest {

    @Test
    fun `Message with mcpToolCall info stores all fields`() {
        val info = McpToolCallInfo(
            toolName = "search",
            serverName = "search-mcp",
            arguments = """{"query":"kotlin"}""",
            durationMs = 150L,
            isError = false
        )
        val msg = Message.mcpToolCall("search-mcp", "search", "Search result", info)
        assertEquals(MessageRole.SYSTEM, msg.role)
        assertEquals("Search result", msg.content)
        assertEquals(info, msg.mcpToolCall)
        assertEquals("search", msg.mcpToolCall!!.toolName)
        assertEquals("search-mcp", msg.mcpToolCall!!.serverName)
        assertEquals("""{"query":"kotlin"}""", msg.mcpToolCall!!.arguments)
        assertEquals(150L, msg.mcpToolCall!!.durationMs)
        assertFalse(msg.mcpToolCall!!.isError)
    }

    @Test
    fun `Message mcpToolCall factory creates SYSTEM role message`() {
        val info = McpToolCallInfo(
            toolName = "save",
            serverName = "save-mcp",
            arguments = "{}"
        )
        val msg = Message.mcpToolCall("save-mcp", "save", "Saved", info)
        assertEquals(MessageRole.SYSTEM, msg.role)
    }

    @Test
    fun `McpToolCallInfo default values`() {
        val info = McpToolCallInfo(
            toolName = "tool",
            serverName = "server",
            arguments = "{}"
        )
        assertNull(info.durationMs)
        assertFalse(info.isError)
    }

    @Test
    fun `Message without mcpToolCall has null info`() {
        val msg = Message(MessageRole.USER, "Hello")
        assertNull(msg.mcpToolCall)
    }
}
