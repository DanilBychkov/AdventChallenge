package org.bothubclient.domain.entity

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val maxTokens: Int = 150,
    val temperature: Double = 0.7
)
