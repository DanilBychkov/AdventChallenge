package org.bothubclient.infrastructure.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiChatMessage(
    val role: String,
    val content: String? = null,
    val tool_calls: List<ApiToolCall>? = null,
    val tool_call_id: String? = null,
    val name: String? = null
) {
    constructor(role: String, content: String) : this(
        role = role,
        content = content,
        tool_calls = null,
        tool_call_id = null,
        name = null
    )
}

@Serializable
data class ApiToolCall(
    val id: String,
    val type: String = "function",
    val function: ApiFunctionCall
)

@Serializable
data class ApiFunctionCall(
    val name: String,
    val arguments: String
)

@Serializable
data class ApiToolDefinition(
    val type: String = "function",
    val function: ApiFunctionDef
)

@Serializable
data class ApiFunctionDef(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class ApiChatRequest(
    val model: String,
    val messages: List<ApiChatMessage>,
    val max_tokens: Int = 150,
    val temperature: Double = 0.7,
    val tools: List<ApiToolDefinition>? = null
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
data class ApiChoiceMessage(
    val role: String? = null,
    val content: String? = null,
    val tool_calls: List<ApiToolCall>? = null
)

@Serializable
data class ApiChatChoice(
    val index: Int? = null,
    val message: ApiChoiceMessage? = null,
    val finish_reason: String? = null
)

@Serializable
data class ApiChatError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
