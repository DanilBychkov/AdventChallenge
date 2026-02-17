package org.bothubclient.domain.entity

enum class MessageRole {
    USER, ASSISTANT, SYSTEM, ERROR
}

data class Message(
    val role: MessageRole,
    val content: String,
    val timestamp: String = java.time.LocalTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
) {
    companion object {
        fun user(content: String) = Message(MessageRole.USER, content)
        fun assistant(content: String) = Message(MessageRole.ASSISTANT, content)
        fun error(content: String) = Message(MessageRole.ERROR, content)
        fun system(content: String) = Message(MessageRole.SYSTEM, content)
    }
}
