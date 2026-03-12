package org.bothubclient.domain.entity

enum class MessageRole {
    USER, ASSISTANT, SYSTEM, ERROR
}

data class MessageMetrics(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val responseTimeMs: Long = 0,
    val cost: Double? = null
)

data class McpToolCallInfo(
    val toolName: String,
    val serverName: String,
    val arguments: String,
    val durationMs: Long? = null,
    val isError: Boolean = false
)

data class Message(
    val role: MessageRole,
    val content: String,
    val timestamp: String = java.time.LocalTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
    val metrics: MessageMetrics? = null,
    val mcpToolCall: McpToolCallInfo? = null
) {
    companion object {
        fun user(content: String) = Message(MessageRole.USER, content)
        fun assistant(content: String, metrics: MessageMetrics? = null) =
            Message(MessageRole.ASSISTANT, content, metrics = metrics)
        fun error(content: String) = Message(MessageRole.ERROR, content)
        fun system(content: String) = Message(MessageRole.SYSTEM, content)
        fun mcpToolCall(serverName: String, toolName: String, content: String, info: McpToolCallInfo) =
            Message(MessageRole.SYSTEM, content, mcpToolCall = info)
    }
}
