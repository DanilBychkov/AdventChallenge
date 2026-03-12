package org.bothubclient.infrastructure.agent

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.domain.entity.MessageRole
import org.bothubclient.infrastructure.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BothubChatAgentToolCallingTest {

    private val json = Json { ignoreUnknownKeys = true }

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

    private fun textResponse(content: String, promptTokens: Int = 10, completionTokens: Int = 20) =
        ApiChatResponse(
            id = "chat-1",
            choices = listOf(
                ApiChatChoice(
                    index = 0,
                    message = ApiChoiceMessage(role = "assistant", content = content),
                    finish_reason = "stop"
                )
            ),
            usage = ApiUsage(
                prompt_tokens = promptTokens,
                completion_tokens = completionTokens,
                total_tokens = promptTokens + completionTokens
            )
        )

    private fun toolCallResponse(toolCalls: List<ApiToolCall>, promptTokens: Int = 10, completionTokens: Int = 25) =
        ApiChatResponse(
            id = "chat-1",
            choices = listOf(
                ApiChatChoice(
                    index = 0,
                    message = ApiChoiceMessage(role = "assistant", content = null, tool_calls = toolCalls),
                    finish_reason = "tool_calls"
                )
            ),
            usage = ApiUsage(
                prompt_tokens = promptTokens,
                completion_tokens = completionTokens,
                total_tokens = promptTokens + completionTokens
            )
        )

    private fun toolDef(name: String) = ApiToolDefinition(
        type = "function",
        function = ApiFunctionDef(
            name = name,
            description = "Test $name",
            parameters = buildJsonObject { put("type", "object") })
    )

    @Test
    fun `sendWithTools with no tool_calls returns text response immediately`() = runTest {
        val client = createClient(listOf(textResponse("Hello, here is your answer!")))
        val agent = BothubChatAgent(client = client, getApiKey = { "test-key" })

        val result = agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Hi",
            model = "gpt-4o",
            systemPrompt = "You are helpful.",
            temperature = 0.7,
            tools = listOf(toolDef("search")),
            toolExecutor = { _, _ -> "unused" },
            onToolCall = { _, _, _, _ -> }
        )

        assertTrue(result is ChatResult.Success)
        assertEquals("Hello, here is your answer!", (result as ChatResult.Success).message.content)
        assertEquals(MessageRole.ASSISTANT, result.message.role)
    }

    @Test
    fun `sendWithTools with single tool_call executes tool and loops`() = runTest {
        val searchCall = ApiToolCall(
            id = "call_1",
            type = "function",
            function = ApiFunctionCall(name = "search", arguments = """{"query":"test"}""")
        )
        val client = createClient(
            listOf(
                toolCallResponse(listOf(searchCall), promptTokens = 15, completionTokens = 30),
                textResponse("I found: test result", promptTokens = 50, completionTokens = 10)
            )
        )
        val agent = BothubChatAgent(client = client, getApiKey = { "test-key" })
        var toolExecuted = false

        val result = agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Search for test",
            model = "gpt-4o",
            systemPrompt = "You are helpful.",
            temperature = 0.7,
            tools = listOf(toolDef("search")),
            toolExecutor = { name, args ->
                toolExecuted = true
                assertEquals("search", name)
                assertEquals("""{"query":"test"}""", args)
                "Found: test result"
            },
            onToolCall = { _, _, _, _ -> }
        )

        assertTrue(toolExecuted)
        assertTrue(result is ChatResult.Success)
        assertEquals("I found: test result", (result as ChatResult.Success).message.content)
    }

    @Test
    fun `sendWithTools with 3 sequential tool_calls completes pipeline`() = runTest {
        val responses = listOf(
            toolCallResponse(
                listOf(ApiToolCall("c1", "function", ApiFunctionCall("search", """{"q":"kotlin"}"""))),
                promptTokens = 20,
                completionTokens = 25
            ),
            toolCallResponse(
                listOf(ApiToolCall("c2", "function", ApiFunctionCall("summarize", """{"text":"raw"}"""))),
                promptTokens = 60,
                completionTokens = 20
            ),
            toolCallResponse(
                listOf(ApiToolCall("c3", "function", ApiFunctionCall("save", """{"path":"out.txt","content":"x"}"""))),
                promptTokens = 100,
                completionTokens = 15
            ),
            textResponse("Done! Saved to out.txt", promptTokens = 150, completionTokens = 8)
        )
        val client = createClient(responses)
        val agent = BothubChatAgent(client = client, getApiKey = { "test-key" })
        val callOrder = mutableListOf<String>()

        val result = agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Search, summarize, save",
            model = "gpt-4o",
            systemPrompt = "You are helpful.",
            temperature = 0.7,
            tools = listOf(toolDef("search"), toolDef("summarize"), toolDef("save")),
            toolExecutor = { name, _ ->
                callOrder.add(name)
                when (name) {
                    "search" -> "raw"
                    "summarize" -> "x"
                    "save" -> "ok"
                    else -> "unknown"
                }
            },
            onToolCall = { _, _, _, _ -> }
        )

        assertEquals(listOf("search", "summarize", "save"), callOrder)
        assertTrue(result is ChatResult.Success)
        assertEquals("Done! Saved to out.txt", (result as ChatResult.Success).message.content)
    }

    @Test
    fun `sendWithTools calls toolExecutor with correct arguments`() = runTest {
        val call = ApiToolCall("id1", "function", ApiFunctionCall("get_weather", """{"city":"Moscow","unit":"c"}"""))
        val client = createClient(
            listOf(
                toolCallResponse(listOf(call)),
                textResponse("Weather: 15C")
            )
        )
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })
        var capturedName: String? = null
        var capturedArgs: String? = null

        agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Weather?",
            model = "gpt-4o",
            systemPrompt = "Help",
            temperature = 0.7,
            tools = listOf(toolDef("get_weather")),
            toolExecutor = { name, args ->
                capturedName = name
                capturedArgs = args
                "15C"
            },
            onToolCall = { _, _, _, _ -> }
        )

        assertEquals("get_weather", capturedName)
        assertEquals("""{"city":"Moscow","unit":"c"}""", capturedArgs)
    }

    @Test
    fun `sendWithTools invokes onToolCall callback for each call`() = runTest {
        val call1 = ApiToolCall("c1", "function", ApiFunctionCall("a", "{}"))
        val call2 = ApiToolCall("c2", "function", ApiFunctionCall("b", "{}"))
        val client = createClient(
            listOf(
                toolCallResponse(listOf(call1)),
                toolCallResponse(listOf(call2)),
                textResponse("Final")
            )
        )
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })
        val callbacks = mutableListOf<Triple<String, String, String>>()

        agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Go",
            model = "gpt-4o",
            systemPrompt = "Help",
            temperature = 0.7,
            tools = listOf(toolDef("a"), toolDef("b")),
            toolExecutor = { name, args -> "result_$name" },
            onToolCall = { name, args, result, _ -> callbacks.add(Triple(name, args, result)) }
        )

        assertEquals(2, callbacks.size)
        assertEquals(Triple("a", "{}", "result_a"), callbacks[0])
        assertEquals(Triple("b", "{}", "result_b"), callbacks[1])
    }

    @Test
    fun `sendWithTools handles toolExecutor error gracefully`() = runTest {
        val call = ApiToolCall("c1", "function", ApiFunctionCall("failing_tool", """{"x":1}"""))
        val client = createClient(
            listOf(
                toolCallResponse(listOf(call)),
                textResponse("I received the error. Let me explain: the tool failed.")
            )
        )
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })

        val result = agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Use failing tool",
            model = "gpt-4o",
            systemPrompt = "Help",
            temperature = 0.7,
            tools = listOf(toolDef("failing_tool")),
            toolExecutor = { _, _ -> throw RuntimeException("Tool crashed!") },
            onToolCall = { _, _, _, _ -> }
        )

        assertTrue(result is ChatResult.Success)
        assertTrue(
            (result as ChatResult.Success).message.content.contains("error") ||
                    result.message.content.contains("Tool") ||
                    result.message.content.contains("crashed") ||
                    result.message.content.contains("failed")
        )
    }

    @Test
    fun `sendWithTools respects max iteration limit`() = runTest {
        val toolResponses = (0..9).map { i ->
            toolCallResponse(
                listOf(ApiToolCall("c$i", "function", ApiFunctionCall("loop", """{"i":$i}"""))),
                promptTokens = 10 + i * 5,
                completionTokens = 15
            )
        }
        val client = createClient(toolResponses)
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })
        var execCount = 0

        val result = agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Loop",
            model = "gpt-4o",
            systemPrompt = "Help",
            temperature = 0.7,
            tools = listOf(toolDef("loop")),
            toolExecutor = { _, _ ->
                execCount++
                "ok"
            },
            onToolCall = { _, _, _, _ -> }
        )

        assertEquals(10, execCount)
        assertTrue(result is ChatResult.Success)
        assertTrue((result as ChatResult.Success).message.content.contains("maximum iterations"))
    }

    @Test
    fun `sendWithTools with empty tools list falls back to regular response`() = runTest {
        val client = createClient(listOf(textResponse("Plain text reply")))
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })
        var toolExecutorCalled = false

        val result = agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Hello",
            model = "gpt-4o",
            systemPrompt = "Help",
            temperature = 0.7,
            tools = emptyList(),
            toolExecutor = { _, _ ->
                toolExecutorCalled = true
                "never"
            },
            onToolCall = { _, _, _, _ -> }
        )

        assertFalse(toolExecutorCalled)
        assertTrue(result is ChatResult.Success)
        assertEquals("Plain text reply", (result as ChatResult.Success).message.content)
    }

    @Test
    fun `sendWithTools includes tool definitions in API request body`() = runTest {
        var capturedBody: String? = null
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler { request ->
                    capturedBody = when (val content = request.body) {
                        is OutgoingContent.ByteArrayContent -> String(content.bytes(), Charsets.UTF_8)
                        is OutgoingContent.WriteChannelContent -> {
                            val channel = ByteChannel()
                            content.writeTo(channel)
                            channel.close()
                            channel.readRemaining().readText()
                        }

                        else -> ""
                    }
                    respond(
                        content = json.encodeToString(ApiChatResponse.serializer(), textResponse("ok")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })
        val tools = listOf(
            toolDef("search"),
            toolDef("summarize")
        )

        agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Test",
            model = "gpt-4o",
            systemPrompt = "Help",
            temperature = 0.7,
            tools = tools,
            toolExecutor = { _, _ -> "x" },
            onToolCall = { _, _, _, _ -> }
        )

        assertNotNull(capturedBody)
        assertTrue(capturedBody!!.contains("\"tools\""))
        assertTrue(capturedBody.contains("search"))
        assertTrue(capturedBody.contains("summarize"))
        assertTrue(
            capturedBody.contains("\"max_tokens\":${org.bothubclient.config.ApiConfig.TOOL_CALLING_MAX_TOKENS}"),
            "sendWithTools must use TOOL_CALLING_MAX_TOKENS (${org.bothubclient.config.ApiConfig.TOOL_CALLING_MAX_TOKENS}), not DEFAULT_MAX_TOKENS"
        )
    }

    @Test
    fun `sendWithTools adds tool result messages with correct role`() = runTest {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler { request ->
                    requestCount++
                    val bodyText = when (val content = request.body) {
                        is OutgoingContent.ByteArrayContent -> String(content.bytes(), Charsets.UTF_8)
                        is OutgoingContent.WriteChannelContent -> {
                            val channel = ByteChannel()
                            content.writeTo(channel)
                            channel.close()
                            channel.readRemaining().readText()
                        }

                        else -> ""
                    }
                    val messages = json.parseToJsonElement(bodyText).jsonObject["messages"]?.jsonArray
                    val lastMsg = messages?.lastOrNull()?.jsonObject
                    val role = lastMsg?.get("role")?.toString()

                    val response = when (requestCount) {
                        1 -> toolCallResponse(
                            listOf(
                                ApiToolCall("c1", "function", ApiFunctionCall("test", "{}"))
                            )
                        )

                        2 -> {
                            assertNotNull(role, "Second request should have tool result")
                            assertTrue(role!!.contains("tool"), "Expected tool role, got: $role")
                            textResponse("Got it")
                        }

                        else -> error("Unexpected request")
                    }
                    respond(
                        content = json.encodeToString(ApiChatResponse.serializer(), response),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })

        val result = agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Test",
            model = "gpt-4o",
            systemPrompt = "Help",
            temperature = 0.7,
            tools = listOf(toolDef("test")),
            toolExecutor = { _, _ -> "tool result" },
            onToolCall = { _, _, _, _ -> }
        )

        assertTrue(result is ChatResult.Success)
        assertEquals(2, requestCount)
    }

    @Test
    fun `sendWithTools passes system prompt to API without docs-not-loaded text`() = runTest {
        var capturedSystemPrompt: String? = null
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler { request ->
                    val bodyText = when (val content = request.body) {
                        is OutgoingContent.ByteArrayContent -> String(content.bytes(), Charsets.UTF_8)
                        is OutgoingContent.WriteChannelContent -> {
                            val channel = ByteChannel()
                            content.writeTo(channel)
                            channel.close()
                            channel.readRemaining().readText()
                        }

                        else -> ""
                    }
                    val messages = json.parseToJsonElement(bodyText).jsonObject["messages"]?.jsonArray
                    val systemMsg = messages?.firstOrNull()?.jsonObject
                    capturedSystemPrompt = systemMsg?.get("content")?.toString()

                    respond(
                        content = json.encodeToString(
                            ApiChatResponse.serializer(),
                            toolCallResponse(
                                listOf(
                                    ApiToolCall("c1", "function", ApiFunctionCall("search", """{"query":"Python"}"""))
                                )
                            )
                        ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })

        val toolCallingSystemPrompt = "You are helpful.\n\nИНСТРУКЦИИ ПО ИСПОЛЬЗОВАНИЮ ИНСТРУМЕНТОВ: use tools"

        runCatching {
            agent.sendWithTools(
                sessionId = "s1",
                contextMessages = emptyList(),
                userMessage = "Найди информацию о Python в Википедии",
                model = "gemini-2.0-flash-lite-001",
                systemPrompt = toolCallingSystemPrompt,
                temperature = 0.7,
                tools = listOf(toolDef("search"), toolDef("summarize"), toolDef("save-to-file")),
                toolExecutor = { _, _ -> "result" },
                onToolCall = { _, _, _, _ -> }
            )
        }

        assertNotNull(capturedSystemPrompt)
        assertTrue(
            capturedSystemPrompt!!.contains("ИНСТРУКЦИИ ПО ИСПОЛЬЗОВАНИЮ ИНСТРУМЕНТОВ"),
            "System prompt sent to API must contain tool calling instructions"
        )
        assertFalse(
            capturedSystemPrompt!!.contains("Документация не загрузилась"),
            "System prompt sent to API must NOT contain 'docs not loaded' fallback text"
        )
    }

    @Test
    fun `parseTextToolCall extracts tool name and arguments from TOOL_CALL tags`() {
        val content = """[TOOL_CALL]
{"name": "search", "arguments": {"query": "Python"}}
[/TOOL_CALL]"""
        val result = BothubChatAgent.parseTextToolCall(content)
        assertNotNull(result)
        assertEquals("search", result!!.first)
        assertTrue(result.second.contains("Python"))
    }

    @Test
    fun `parseTextToolCall returns null when no TOOL_CALL tags present`() {
        val content = "This is a regular response without any tool calls."
        val result = BothubChatAgent.parseTextToolCall(content)
        assertNull(result)
    }

    @Test
    fun `parseTextToolCall returns null for empty content`() {
        assertNull(BothubChatAgent.parseTextToolCall(""))
    }

    @Test
    fun `parseTextToolCall handles missing arguments field`() {
        val content = """[TOOL_CALL]
{"name": "search"}
[/TOOL_CALL]"""
        val result = BothubChatAgent.parseTextToolCall(content)
        assertNotNull(result)
        assertEquals("search", result!!.first)
        assertEquals("{}", result.second)
    }

    @Test
    fun `parseTextToolCall handles complex arguments`() {
        val content = """[TOOL_CALL]
{"name": "save-to-file", "arguments": {"content": "Hello world", "filename": "test.txt"}}
[/TOOL_CALL]"""
        val result = BothubChatAgent.parseTextToolCall(content)
        assertNotNull(result)
        assertEquals("save-to-file", result!!.first)
        assertTrue(result.second.contains("Hello world"))
        assertTrue(result.second.contains("test.txt"))
    }

    @Test
    fun `parseTextToolCall ignores text outside TOOL_CALL tags`() {
        val content = """Some preamble text
[TOOL_CALL]
{"name": "summarize", "arguments": {"text": "long text here"}}
[/TOOL_CALL]
Some trailing text"""
        val result = BothubChatAgent.parseTextToolCall(content)
        assertNotNull(result)
        assertEquals("summarize", result!!.first)
    }

    @Test
    fun `parseTextToolCall returns null for invalid JSON in tags`() {
        val content = """[TOOL_CALL]
not valid json
[/TOOL_CALL]"""
        val result = BothubChatAgent.parseTextToolCall(content)
        assertNull(result)
    }

    @Test
    fun `parseTextToolCall returns null when name is missing`() {
        val content = """[TOOL_CALL]
{"arguments": {"query": "test"}}
[/TOOL_CALL]"""
        val result = BothubChatAgent.parseTextToolCall(content)
        assertNull(result)
    }

    @Test
    fun `sendWithTools text-based fallback executes tool from TOOL_CALL tags`() = runTest {
        var requestCount = 0
        val executedTools = mutableListOf<String>()

        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler {
                    requestCount++
                    val response = when (requestCount) {
                        1 -> textResponse(
                            """[TOOL_CALL]
{"name": "search", "arguments": {"query": "Kotlin"}}
[/TOOL_CALL]"""
                        )

                        2 -> textResponse("Here is the search result about Kotlin.")
                        else -> error("Unexpected request")
                    }
                    respond(
                        content = json.encodeToString(ApiChatResponse.serializer(), response),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })

        val result = agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Search Kotlin",
            model = "gemini-2.0-flash-lite-001",
            systemPrompt = "Help",
            temperature = 0.7,
            tools = listOf(toolDef("search")),
            toolExecutor = { name, args ->
                executedTools.add(name)
                """{"title": "Kotlin", "extract": "Kotlin is a language"}"""
            },
            onToolCall = { _, _, _, _ -> }
        )

        assertTrue(result is ChatResult.Success)
        assertEquals(1, executedTools.size)
        assertEquals("search", executedTools[0])
        assertEquals(2, requestCount)
        assertEquals("Here is the search result about Kotlin.", (result as ChatResult.Success).message.content)
    }

    @Test
    fun `sendWithTools text-based fallback handles full pipeline`() = runTest {
        var requestCount = 0
        val executedTools = mutableListOf<String>()

        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler {
                    requestCount++
                    val response = when (requestCount) {
                        1 -> textResponse(
                            """[TOOL_CALL]
{"name": "search", "arguments": {"query": "Python"}}
[/TOOL_CALL]"""
                        )

                        2 -> textResponse(
                            """[TOOL_CALL]
{"name": "summarize", "arguments": {"text": "Python is a language"}}
[/TOOL_CALL]"""
                        )

                        3 -> textResponse(
                            """[TOOL_CALL]
{"name": "save-to-file", "arguments": {"content": "Summary", "filename": "result.txt"}}
[/TOOL_CALL]"""
                        )

                        4 -> textResponse("Done! Saved to result.txt")
                        else -> error("Unexpected request")
                    }
                    respond(
                        content = json.encodeToString(ApiChatResponse.serializer(), response),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })

        val result = agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Search, summarize, save",
            model = "gemini-2.0-flash-lite-001",
            systemPrompt = "Help",
            temperature = 0.7,
            tools = listOf(toolDef("search"), toolDef("summarize"), toolDef("save-to-file")),
            toolExecutor = { name, _ ->
                executedTools.add(name)
                "result_$name"
            },
            onToolCall = { _, _, _, _ -> }
        )

        assertEquals(listOf("search", "summarize", "save-to-file"), executedTools)
        assertTrue(result is ChatResult.Success)
        assertEquals("Done! Saved to result.txt", (result as ChatResult.Success).message.content)
        assertEquals(4, requestCount)
    }

    @Test
    fun `sendWithTools accumulates tokens from all iterations`() = runTest {
        val client = createClient(
            listOf(
                toolCallResponse(
                    listOf(
                        ApiToolCall("c1", "function", ApiFunctionCall("x", "{}"))
                    ), promptTokens = 10, completionTokens = 20
                ),
                toolCallResponse(
                    listOf(
                        ApiToolCall("c2", "function", ApiFunctionCall("y", "{}"))
                    ), promptTokens = 50, completionTokens = 25
                ),
                textResponse("Done", promptTokens = 100, completionTokens = 5)
            )
        )
        val agent = BothubChatAgent(client = client, getApiKey = { "key" })

        val result = agent.sendWithTools(
            sessionId = "s1",
            contextMessages = emptyList(),
            userMessage = "Multi",
            model = "gpt-4o",
            systemPrompt = "Help",
            temperature = 0.7,
            tools = listOf(toolDef("x"), toolDef("y")),
            toolExecutor = { n, _ -> "r_$n" },
            onToolCall = { _, _, _, _ -> }
        )

        assertTrue(result is ChatResult.Success)
        val metrics = (result as ChatResult.Success).metrics
        assertEquals(10 + 50 + 100, metrics.promptTokens)
        assertEquals(20 + 25 + 5, metrics.completionTokens)
        assertEquals(210, metrics.totalTokens)
    }
}
