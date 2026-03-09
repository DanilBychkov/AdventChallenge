package org.bothubclient.infrastructure.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import org.bothubclient.application.mcp.McpClient
import org.bothubclient.application.mcp.McpFetchResult
import org.bothubclient.application.mcp.McpHealthResult
import org.bothubclient.domain.entity.McpServerConfig
import org.bothubclient.domain.entity.McpTransportType
import org.bothubclient.domain.logging.Logger
import org.bothubclient.domain.logging.NoOpLogger
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.charset.StandardCharsets

class StdioMcpClient(
    private val logger: Logger = NoOpLogger,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : McpClient {
    private companion object {
        const val TAG = "StdioMcpClient"
        const val FETCH_TIMEOUT_MS = 30_000L
        const val HEALTH_TIMEOUT_MS = 10_000L
        const val JSON_RPC_VERSION = "2.0"
    }

    override suspend fun fetchContext(server: McpServerConfig, query: String): McpFetchResult {
        if (server.transportType != McpTransportType.STDIO) {
            return unsupportedTransportFetch(server)
        }

        if (query.isBlank()) {
            return McpFetchResult.Failure(reason = "Query is blank")
                .also { logFetch(server.id, success = false, reason = "Query is blank") }
        }

        return try {
            val result = withContext(Dispatchers.IO) {
                runMcpSession(
                    server = server,
                    timeoutMs = FETCH_TIMEOUT_MS
                ) { session ->
                    val toolsResponse = session.request(
                        method = "tools/list",
                        params = buildJsonObject { }
                    )

                    val tools = extractTools(toolsResponse)
                    if (tools.isEmpty()) {
                        return@runMcpSession McpFetchResult.Failure("No tools returned by MCP server")
                    }

                    val attemptResult = callToolsForQuery(session, tools, query)
                    attemptResult
                }
            }

            when (result) {
                is McpFetchResult.Success -> {
                    logFetch(server.id, success = true, reason = null)
                    result
                }

                is McpFetchResult.Failure -> {
                    logFetch(server.id, success = false, reason = result.reason)
                    result
                }
            }
        } catch (t: Throwable) {
            McpFetchResult.Failure(reason = "Unexpected fetch error: ${t.message ?: "unknown"}", throwable = t)
                .also { logFetch(server.id, success = false, reason = it.reason) }
        }
    }

    override suspend fun checkHealth(server: McpServerConfig): McpHealthResult {
        if (server.transportType != McpTransportType.STDIO) {
            val message = "HTTP transport is not implemented yet"
            logFetch(server.id, success = false, reason = message)
            return McpHealthResult.Error(message = message)
        }

        return try {
            withContext(Dispatchers.IO) {
                runMcpSession(
                    server = server,
                    timeoutMs = HEALTH_TIMEOUT_MS
                ) { session ->
                    session.request(
                        method = "tools/list",
                        params = buildJsonObject { }
                    )
                    McpHealthResult.Online
                }
            }
            logger.log(TAG, "MCP health server=${server.id} success=true")
            McpHealthResult.Online
        } catch (t: Throwable) {
            McpHealthResult.Error("Unexpected healthcheck error: ${t.message ?: "unknown"}", t).also {
                logger.log(TAG, "MCP health server=${server.id} success=false reason=${it.message}")
            }
        }
    }

    private suspend fun <T> runMcpSession(
        server: McpServerConfig,
        timeoutMs: Long,
        block: suspend (McpSession) -> T
    ): T {
        val command = server.command?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("MCP command is missing for server=${server.id}")

        val processBuilder = ProcessBuilder(listOf(command) + (server.args ?: emptyList()))
        server.env.orEmpty().forEach { (key, value) -> processBuilder.environment()[key] = value }
        val process = processBuilder.start()

        val output = process.outputStream
        val input = PushbackInputStream(process.inputStream, 1)
        val errors = process.errorStream
        val session = McpSession(input = input, output = output, timeoutMs = timeoutMs)

        return try {
            initialize(session)
            block(session)
        } finally {
            runCatching {
                output.close()
                input.close()
                errors.close()
            }
            if (process.isAlive) {
                process.destroy()
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            }
        }
    }

    private suspend fun initialize(session: McpSession) {
        session.request(
            method = "initialize",
            params = buildJsonObject {
                put("protocolVersion", "2024-11-05")
                putJsonObject("clientInfo") {
                    put("name", "bothubclient")
                    put("version", "1.0.0")
                }
                putJsonObject("capabilities") { }
            }
        )

        session.notification(
            method = "notifications/initialized",
            params = buildJsonObject { }
        )
    }

    private suspend fun callToolsForQuery(
        session: McpSession,
        tools: List<JsonObject>,
        query: String
    ): McpFetchResult {
        for (tool in prioritizeTools(tools)) {
            val toolName = tool["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val argumentCandidates = buildArgumentCandidates(tool, query)

            for (arguments in argumentCandidates) {
                val response = runCatching {
                    session.request(
                        method = "tools/call",
                        params = buildJsonObject {
                            put("name", toolName)
                            put("arguments", arguments)
                        }
                    )
                }.getOrNull() ?: continue

                val content = extractContent(response)
                if (content.isNotBlank()) {
                    return McpFetchResult.Success(content)
                }
            }
        }

        return McpFetchResult.Failure("MCP server returned no usable content for query")
    }

    private fun prioritizeTools(tools: List<JsonObject>): List<JsonObject> {
        val weights = mapOf(
            "get-library-docs" to 0,
            "search" to 1,
            "query" to 2,
            "lookup" to 3,
            "resolve-library-id" to 4
        )

        return tools.sortedBy { tool ->
            val name = tool["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            weights.entries.firstOrNull { name.contains(it.key, ignoreCase = true) }?.value ?: 10
        }
    }

    private fun buildArgumentCandidates(tool: JsonObject, query: String): List<JsonObject> {
        val toolName = tool["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val schema = tool["inputSchema"]?.jsonObject
        val properties = schema?.get("properties")?.jsonObject.orEmpty()
        val required = schema?.get("required")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

        val inferred = buildJsonObject {
            val keys = if (required.isNotEmpty()) required else properties.keys.toList()
            keys.forEach { key -> put(key, query) }
            if (keys.isEmpty()) {
                put("query", query)
            }
        }

        val generic = buildJsonObject { put("query", query) }

        val context7Specific = buildJsonObject {
            put("libraryName", query)
            put("context7CompatibleLibraryID", query)
            put("topic", query)
            put("tokens", 6000)
        }

        return buildList {
            if (toolName.contains("get-library-docs", ignoreCase = true)) add(context7Specific)
            if (toolName.contains("resolve-library-id", ignoreCase = true)) {
                add(buildJsonObject { put("libraryName", query) })
            }
            add(inferred)
            add(generic)
        }.distinctBy { it.toString() }
    }

    private fun extractTools(response: JsonObject): List<JsonObject> {
        val tools = response["result"]
            ?.jsonObject
            ?.get("tools")
            ?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()
        return tools
    }

    private fun extractContent(response: JsonObject): String {
        val result = response["result"] ?: return ""
        return extractTextFromJson(result).trim()
    }

    private fun extractTextFromJson(element: JsonElement): String {
        return when (element) {
            is JsonPrimitive -> element.contentOrNull.orEmpty()
            is JsonArray -> element.joinToString("\n") { extractTextFromJson(it) }.trim()
            is JsonObject -> {
                val preferredKeys = listOf("text", "content", "message", "value", "output")
                for (key in preferredKeys) {
                    val value = element[key] ?: continue
                    val text = extractTextFromJson(value)
                    if (text.isNotBlank()) {
                        return text
                    }
                }
                element.values.joinToString("\n") { extractTextFromJson(it) }.trim()
            }
        }
    }

    private fun unsupportedTransportFetch(server: McpServerConfig): McpFetchResult {
        val message = "HTTP transport is not implemented yet"
        logFetch(server.id, success = false, reason = message)
        return McpFetchResult.Failure(message)
    }

    private fun logFetch(serverId: String, success: Boolean, reason: String?) {
        val suffix = if (reason.isNullOrBlank()) "" else " reason=$reason"
        logger.log(TAG, "MCP fetch server=$serverId success=$success$suffix")
    }

    private inner class McpSession(
        private val input: PushbackInputStream,
        private val output: OutputStream,
        private val timeoutMs: Long
    ) {
        private var nextId = 1

        suspend fun request(method: String, params: JsonObject): JsonObject {
            val requestId = nextId++
            val payload = buildJsonObject {
                put("jsonrpc", JSON_RPC_VERSION)
                put("id", requestId)
                put("method", method)
                put("params", params)
            }

            writeMessage(payload)

            val response: JsonObject = withTimeoutOrNull(timeoutMs) {
                readResponseForRequestId(requestId)
            } ?: throw RuntimeException("Timeout waiting for MCP response for method=$method")

            if (response.containsKey("error")) {
                val error = response["error"]?.jsonObject
                val code = error?.get("code")?.jsonPrimitive?.intOrNull
                val messageText = error?.get("message")?.jsonPrimitive?.contentOrNull ?: "Unknown MCP error"
                throw RuntimeException("MCP error code=$code method=$method message=$messageText")
            }

            return response
        }

        fun notification(method: String, params: JsonObject) {
            val payload = buildJsonObject {
                put("jsonrpc", JSON_RPC_VERSION)
                put("method", method)
                put("params", params)
            }
            writeMessage(payload)
        }

        private fun writeMessage(payload: JsonObject) {
            val content = json.encodeToString(JsonObject.serializer(), payload)
            val body = content.toByteArray(StandardCharsets.UTF_8)
            val headers = "Content-Length: ${body.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
            output.write(headers)
            output.write(body)
            output.flush()
        }

        private fun readResponseForRequestId(requestId: Int): JsonObject {
            while (true) {
                val message = readMessage() ?: continue
                val parsed = parseMessage(message) ?: continue
                val id = parsed["id"]?.jsonPrimitive?.intOrNull
                if (id == requestId) {
                    return parsed
                }
            }
        }

        private fun parseMessage(raw: String): JsonObject? {
            return runCatching {
                json.parseToJsonElement(raw).jsonObject
            }.getOrNull()
        }

        private fun readMessage(): String? {
            val firstLine = readLine(input) ?: return null
            if (firstLine.isBlank()) {
                return null
            }

            if (firstLine.startsWith("Content-Length:", ignoreCase = true)) {
                val contentLength = firstLine.substringAfter(":").trim().toIntOrNull() ?: return null

                while (true) {
                    val headerLine = readLine(input) ?: return null
                    if (headerLine.isBlank()) {
                        break
                    }
                }

                val body = ByteArray(contentLength)
                var offset = 0
                while (offset < contentLength) {
                    val read = input.read(body, offset, contentLength - offset)
                    if (read <= 0) {
                        return null
                    }
                    offset += read
                }
                return String(body, StandardCharsets.UTF_8)
            }

            return firstLine
        }
    }

    private fun readLine(input: PushbackInputStream): String? {
        val first = input.read()
        if (first == -1) return null
        input.unread(first)

        val buffer = StringBuilder()
        while (true) {
            val current = input.read()
            if (current == -1) {
                return if (buffer.isEmpty()) null else buffer.toString()
            }
            when (current.toChar()) {
                '\n' -> return buffer.toString().trimEnd('\r')
                else -> buffer.append(current.toChar())
            }
        }
    }
}
