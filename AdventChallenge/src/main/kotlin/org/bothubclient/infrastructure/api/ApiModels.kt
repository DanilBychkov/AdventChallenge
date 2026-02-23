package org.bothubclient.infrastructure.api

import kotlinx.serialization.Serializable

@Serializable
data class ApiChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ApiChatRequest(
    val model: String,
    val messages: List<ApiChatMessage>,
    val max_tokens: Int = 150,
    val temperature: Double = 0.7
)

@Serializable
data class ApiChatResponse(
    val id: String? = null,
    val choices: List<ApiChatChoice>? = null,
    val usage: ApiUsage? = null,
    val error: ApiChatError? = null
)

@Serializable
data class ApiUsage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)

@Serializable
data class ApiChatChoice(
    val index: Int? = null,
    val message: ApiChatMessage? = null,
    val finish_reason: String? = null
)

@Serializable
data class ApiChatError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
