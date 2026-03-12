package org.bothubclient.infrastructure.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bothubclient.config.ApiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiModelsToolCallingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `ApiChatRequest serializes with tools field`() {
        val toolDef = ApiToolDefinition(
            type = "function",
            function = ApiFunctionDef(
                name = "get_weather",
                description = "Get weather",
                parameters = buildJsonObject { put("type", "object") }
            )
        )
        val request = ApiChatRequest(
            model = "gpt-4o",
            messages = listOf(ApiChatMessage("user", "What's the weather?")),
            max_tokens = 150,
            temperature = 0.7,
            tools = listOf(toolDef)
        )
        val serialized = json.encodeToString(ApiChatRequest.serializer(), request)
        assertNotNull(serialized)
        assertEquals(true, serialized.contains("\"tools\""))
        assertEquals(true, serialized.contains("\"get_weather\""))
        assertEquals(true, serialized.contains("\"function\""))
        val roundTrip = json.decodeFromString<ApiChatRequest>(serialized)
        assertEquals(request.model, roundTrip.model)
        assertNotNull(roundTrip.tools)
        assertEquals(1, roundTrip.tools!!.size)
        assertEquals("get_weather", roundTrip.tools!![0].function.name)
    }

    @Test
    fun `ApiChatRequest serializes without tools when null`() {
        val request = ApiChatRequest(
            model = "gpt-4o",
            messages = listOf(ApiChatMessage("user", "Hello")),
            max_tokens = 150,
            temperature = 0.7,
            tools = null
        )
        val serialized = json.encodeToString(ApiChatRequest.serializer(), request)
        assertNotNull(serialized)
        val roundTrip = json.decodeFromString<ApiChatRequest>(serialized)
        assertNull(roundTrip.tools)
    }

    @Test
    fun `ApiChatResponse deserializes tool_calls in choice`() {
        val jsonStr = """
            {
                "id": "chat-123",
                "choices": [{
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": null,
                        "tool_calls": [{
                            "id": "call_abc",
                            "type": "function",
                            "function": {
                                "name": "get_weather",
                                "arguments": "{\"city\": \"Moscow\"}"
                            }
                        }]
                    },
                    "finish_reason": "tool_calls"
                }],
                "usage": {"prompt_tokens": 10, "completion_tokens": 20}
            }
        """.trimIndent()
        val response = json.decodeFromString<ApiChatResponse>(jsonStr)
        assertNotNull(response.choices)
        assertEquals(1, response.choices!!.size)
        val choice = response.choices!![0]
        val message = choice.message
        assertNotNull(message)
        assertNotNull(message!!.tool_calls)
        assertEquals(1, message.tool_calls!!.size)
        val toolCall = message.tool_calls!![0]
        assertEquals("call_abc", toolCall.id)
        assertEquals("function", toolCall.type)
        assertEquals("get_weather", toolCall.function.name)
        assertEquals("{\"city\": \"Moscow\"}", toolCall.function.arguments)
    }

    @Test
    fun `ApiChatResponse deserializes regular text response`() {
        val jsonStr = """
            {
                "id": "chat-456",
                "choices": [{
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "Hello, how can I help?"
                    },
                    "finish_reason": "stop"
                }],
                "usage": {"prompt_tokens": 5, "completion_tokens": 10}
            }
        """.trimIndent()
        val response = json.decodeFromString<ApiChatResponse>(jsonStr)
        assertNotNull(response.choices)
        assertEquals(1, response.choices!!.size)
        val message = response.choices!![0].message
        assertNotNull(message)
        assertEquals("assistant", message!!.role)
        assertEquals("Hello, how can I help?", message.content)
        assertNull(message.tool_calls)
    }

    @Test
    fun `ApiToolDefinition serializes function with parameters`() {
        val toolDef = ApiToolDefinition(
            type = "function",
            function = ApiFunctionDef(
                name = "search",
                description = "Search the web",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { put("query", "string") })
                }
            )
        )
        val serialized = json.encodeToString(ApiToolDefinition.serializer(), toolDef)
        assertNotNull(serialized)
        assertEquals(true, serialized.contains("\"search\""))
        assertEquals(true, serialized.contains("\"Search the web\""))
        assertEquals(true, serialized.contains("\"parameters\""))
        val roundTrip = json.decodeFromString<ApiToolDefinition>(serialized)
        assertEquals("search", roundTrip.function.name)
        assertEquals("Search the web", roundTrip.function.description)
    }

    @Test
    fun `ApiToolCall deserializes with function name and arguments`() {
        val jsonStr = """
            {
                "id": "call_xyz",
                "type": "function",
                "function": {
                    "name": "execute_code",
                    "arguments": "{\"code\": \"print(1)\"}"
                }
            }
        """.trimIndent()
        val toolCall = json.decodeFromString<ApiToolCall>(jsonStr)
        assertEquals("call_xyz", toolCall.id)
        assertEquals("function", toolCall.type)
        assertEquals("execute_code", toolCall.function.name)
        assertEquals("{\"code\": \"print(1)\"}", toolCall.function.arguments)
    }

    @Test
    fun `ApiChatMessage serializes tool role with tool_call_id`() {
        val message = ApiChatMessage(
            role = "tool",
            content = "Weather is sunny",
            tool_calls = null,
            tool_call_id = "call_123",
            name = "get_weather"
        )
        val serialized = json.encodeToString(ApiChatMessage.serializer(), message)
        assertNotNull(serialized)
        assertEquals(true, serialized.contains("\"role\":\"tool\""))
        assertEquals(true, serialized.contains("\"tool_call_id\":\"call_123\""))
        assertEquals(true, serialized.contains("\"name\":\"get_weather\""))
        assertEquals(true, serialized.contains("\"content\":\"Weather is sunny\""))
        val roundTrip = json.decodeFromString<ApiChatMessage>(serialized)
        assertEquals("tool", roundTrip.role)
        assertEquals("call_123", roundTrip.tool_call_id)
        assertEquals("get_weather", roundTrip.name)
        assertEquals("Weather is sunny", roundTrip.content)
    }

    @Test
    fun `TOOL_CALLING_MAX_TOKENS is significantly higher than DEFAULT_MAX_TOKENS`() {
        assertTrue(
            ApiConfig.TOOL_CALLING_MAX_TOKENS > ApiConfig.DEFAULT_MAX_TOKENS,
            "TOOL_CALLING_MAX_TOKENS (${ApiConfig.TOOL_CALLING_MAX_TOKENS}) must be > DEFAULT_MAX_TOKENS (${ApiConfig.DEFAULT_MAX_TOKENS})"
        )
        assertTrue(
            ApiConfig.TOOL_CALLING_MAX_TOKENS >= 1024,
            "TOOL_CALLING_MAX_TOKENS should be at least 1024 for proper tool calling responses"
        )
    }

    @Test
    fun `ApiChatMessage serializes assistant role with tool_calls`() {
        val toolCall = ApiToolCall(
            id = "call_def",
            type = "function",
            function = ApiFunctionCall(name = "fetch_data", arguments = "{}")
        )
        val message = ApiChatMessage(
            role = "assistant",
            content = null,
            tool_calls = listOf(toolCall),
            tool_call_id = null,
            name = null
        )
        val serialized = json.encodeToString(ApiChatMessage.serializer(), message)
        assertNotNull(serialized)
        assertEquals(true, serialized.contains("\"role\":\"assistant\""))
        assertEquals(true, serialized.contains("\"tool_calls\""))
        assertEquals(true, serialized.contains("\"call_def\""))
        assertEquals(true, serialized.contains("\"fetch_data\""))
        val roundTrip = json.decodeFromString<ApiChatMessage>(serialized)
        assertEquals("assistant", roundTrip.role)
        assertNotNull(roundTrip.tool_calls)
        assertEquals(1, roundTrip.tool_calls!!.size)
        assertEquals("call_def", roundTrip.tool_calls!![0].id)
        assertEquals("fetch_data", roundTrip.tool_calls!![0].function.name)
    }
}
