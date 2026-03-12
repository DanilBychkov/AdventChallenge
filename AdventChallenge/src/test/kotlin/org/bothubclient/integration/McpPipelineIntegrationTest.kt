package org.bothubclient.integration

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.infrastructure.agent.BothubChatAgent
import org.bothubclient.infrastructure.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Integration tests for the MCP tool-calling pipeline.
 * Uses BothubChatAgent with MockEngine to simulate LLM responses in sequence,
 * and a mock toolExecutor to verify data flows between search -> summarize -> save.
 */
class McpPipelineIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun toolDef(name: String) = ApiToolDefinition(
        type = "function",
        function = ApiFunctionDef(
            name = name,
            description = "Test $name",
            parameters = buildJsonObject { put("type", "object") })
    )

    private fun textResponse(content: String) = ApiChatResponse(
        id = "chat-1",
        choices = listOf(
            ApiChatChoice(
                index = 0,
                message = ApiChoiceMessage(role = "assistant", content = content),
                finish_reason = "stop"
            )
        ),
        usage = ApiUsage(prompt_tokens = 10, completion_tokens = 20, total_tokens = 30)
    )

    private fun toolCallResponse(toolCalls: List<ApiToolCall>) = ApiChatResponse(
        id = "chat-1",
        choices = listOf(
            ApiChatChoice(
                index = 0,
                message = ApiChoiceMessage(role = "assistant", content = null, tool_calls = toolCalls),
                finish_reason = "tool_calls"
            )
        ),
        usage = ApiUsage(prompt_tokens = 15, completion_tokens = 25, total_tokens = 40)
    )

    private fun createClient(responses: List<ApiChatResponse>): HttpClient {
        var requestIndex = 0
        return HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler {
                    val response = responses.getOrElse(requestIndex) {
                        error("Unexpected request #${requestIndex + 1}, expected at most ${responses.size}")
                    }
                    requestIndex++
                    respond(
                        content = json.encodeToString(ApiChatResponse.serializer(), response),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
    }

    @Test
    fun `full pipeline search-summarize-save executes all 3 tools in order`() = runTest {
        val responses = listOf(
            toolCallResponse(
                listOf(
                    ApiToolCall("c1", "function", ApiFunctionCall("search", """{"query":"Kotlin coroutines"}"""))
                )
            ),
            toolCallResponse(
                listOf(
                    ApiToolCall(
                        "c2",
                        "function",
                        ApiFunctionCall("summarize", """{"text":"Raw search result about Kotlin coroutines..."}""")
                    )
                )
            ),
            toolCallResponse(
                listOf(
                    ApiToolCall(
                        "c3",
                        "function",
                        ApiFunctionCall(
                            "save",
                            """{"path":"summary.txt","content":"Kotlin coroutines: async/await pattern."}"""
                        )
                    )
                )
            ),
            textResponse("I searched for Kotlin coroutines, summarized the results, and saved to summary.txt.")
        )
        val client = createClient(responses)
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })
        val callOrder = mutableListOf<String>()
        val callArgs = mutableListOf<Pair<String, String>>()

        val result = agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Search for Kotlin coroutines, summarize, and save to summary.txt",
            model = "gpt-4o",
            systemPrompt = "You are helpful. Use search, summarize, and save tools.",
            temperature = 0.7,
            tools = listOf(toolDef("search"), toolDef("summarize"), toolDef("save")),
            toolExecutor = { name, args ->
                callOrder.add(name)
                callArgs.add(name to args)
                when (name) {
                    "search" -> "Raw search result about Kotlin coroutines..."
                    "summarize" -> "Kotlin coroutines: async/await pattern."
                    "save" -> "Saved to summary.txt"
                    else -> "unknown"
                }
            },
            onToolCall = { _, _, _, _ -> }
        )

        assertEquals(listOf("search", "summarize", "save"), callOrder)
        assertTrue(result is ChatResult.Success)
        assertEquals(
            "I searched for Kotlin coroutines, summarized the results, and saved to summary.txt.",
            (result as ChatResult.Success).message.content
        )
        assertEquals(4, responses.size)
    }

    @Test
    fun `pipeline with LLM choosing only search and save skipping summarize`() = runTest {
        val responses = listOf(
            toolCallResponse(
                listOf(
                    ApiToolCall("c1", "function", ApiFunctionCall("search", """{"query":"weather"}"""))
                )
            ),
            toolCallResponse(
                listOf(
                    ApiToolCall(
                        "c2",
                        "function",
                        ApiFunctionCall("save", """{"path":"weather.txt","content":"Sunny, 22°C"}""")
                    )
                )
            ),
            textResponse("I searched for weather and saved the result directly to weather.txt.")
        )
        val client = createClient(responses)
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })
        val callOrder = mutableListOf<String>()

        val result = agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Search weather and save it",
            model = "gpt-4o",
            systemPrompt = "Use search and save. Skip summarize if not needed.",
            temperature = 0.7,
            tools = listOf(toolDef("search"), toolDef("summarize"), toolDef("save")),
            toolExecutor = { name, _ ->
                callOrder.add(name)
                when (name) {
                    "search" -> "Sunny, 22°C"
                    "save" -> "ok"
                    else -> "unused"
                }
            },
            onToolCall = { _, _, _, _ -> }
        )

        assertEquals(listOf("search", "save"), callOrder)
        assertFalse(callOrder.contains("summarize"))
        assertTrue(result is ChatResult.Success)
    }

    @Test
    fun `pipeline handles search failure gracefully`() = runTest {
        val responses = listOf(
            toolCallResponse(
                listOf(
                    ApiToolCall("c1", "function", ApiFunctionCall("search", """{"query":"failing"}"""))
                )
            ),
            textResponse("The search failed. I apologize for the inconvenience.")
        )
        val client = createClient(responses)
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })
        var searchWasCalled = false

        val result = agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Search for something",
            model = "gpt-4o",
            systemPrompt = "You are helpful.",
            temperature = 0.7,
            tools = listOf(toolDef("search")),
            toolExecutor = { name, _ ->
                searchWasCalled = true
                if (name == "search") throw RuntimeException("Search service unavailable")
                "ok"
            },
            onToolCall = { _, _, _, _ -> }
        )

        assertTrue(searchWasCalled)
        assertTrue(result is ChatResult.Success)
        val content = (result as ChatResult.Success).message.content
        assertTrue(
            content.contains("fail") || content.contains("error") || content.contains("apologize") || content.contains("unavailable"),
            "Expected graceful handling of search failure, got: $content"
        )
    }

    @Test
    fun `pipeline data flows correctly between tools`() = runTest {
        var searchResult: String? = null
        var summarizeInput: String? = null
        var saveInput: String? = null
        val responses = listOf(
            toolCallResponse(
                listOf(
                    ApiToolCall("c1", "function", ApiFunctionCall("search", """{"query":"test"}"""))
                )
            ),
            toolCallResponse(
                listOf(
                    ApiToolCall("c2", "function", ApiFunctionCall("summarize", """{"text":"SEARCH_OUTPUT_DATA"}"""))
                )
            ),
            toolCallResponse(
                listOf(
                    ApiToolCall(
                        "c3",
                        "function",
                        ApiFunctionCall("save", """{"path":"out.txt","content":"SUMMARY_OUTPUT_DATA"}""")
                    )
                )
            ),
            textResponse("Done")
        )
        val client = createClient(responses)
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })

        agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Search, summarize, save",
            model = "gpt-4o",
            systemPrompt = "Help",
            temperature = 0.7,
            tools = listOf(toolDef("search"), toolDef("summarize"), toolDef("save")),
            toolExecutor = { name, args ->
                when (name) {
                    "search" -> {
                        searchResult = "SEARCH_OUTPUT_DATA"
                        searchResult!!
                    }

                    "summarize" -> {
                        summarizeInput = args
                        "SUMMARY_OUTPUT_DATA"
                    }

                    "save" -> {
                        saveInput = args
                        "saved"
                    }

                    else -> "?"
                }
            },
            onToolCall = { _, _, _, _ -> }
        )

        assertEquals("SEARCH_OUTPUT_DATA", searchResult)
        assertTrue(
            summarizeInput!!.contains("SEARCH_OUTPUT_DATA"),
            "Summarize should receive search output, got: $summarizeInput"
        )
        assertTrue(saveInput!!.contains("SUMMARY_OUTPUT_DATA"), "Save should receive summarize output, got: $saveInput")
    }
}
