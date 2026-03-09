package org.bothubclient.domain.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChatResultTest {

    @Test
    fun `Success has mcpError null by default`() {
        val result = ChatResult.Success(message = Message.assistant("Hi"), metrics = RequestMetrics())
        assertNull(result.mcpError)
    }

    @Test
    fun `Success copy preserves mcpError`() {
        val result = ChatResult.Success(
            message = Message.assistant("Doc failed"),
            metrics = RequestMetrics(),
            mcpError = "timeout"
        )
        assertEquals("timeout", result.mcpError)
        val copied = result.copy(mcpError = "resolve returned no ID")
        assertEquals("resolve returned no ID", copied.mcpError)
    }
}
