package org.bothubclient.infrastructure.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import org.bothubclient.application.mcp.*
import org.bothubclient.domain.entity.McpServerConfig
import org.bothubclient.domain.entity.McpTransportType
import org.bothubclient.domain.logging.Logger
import org.bothubclient.domain.logging.NoOpLogger
import org.bothubclient.infrastructure.logging.AppLogger
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class StdioMcpClient(
    private val logger: Logger = NoOpLogger,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val fetchStrategySelector: (McpServerConfig) -> StdioMcpFetchStrategy = { DefaultStdioMcpFetchStrategy() }
) : McpClient {
    private companion object {
        const val TAG = "StdioMcpClient"
        const val DISCOVER_TIMEOUT_MS = 30_000L
        const val FETCH_TIMEOUT_MS = 30_000L
        const val HEALTH_TIMEOUT_MS = 30_000L
        const val JSON_RPC_VERSION = "2.0"
    }

    override suspend fun discover(server: McpServerConfig): McpDiscoveryResult? {
        if (server.transportType != McpTransportType.STDIO) return null
        return try {
            withContext(Dispatchers.IO) {
                runMcpSession(
                    server = server,
                    timeoutMs = DISCOVER_TIMEOUT_MS
                ) { session ->
                    val toolsResponse = session.request(
                        method = "tools/list",
                        params = buildJsonObject { }
                    )
                    val tools = extractTools(toolsResponse)
                    val toolInfos = tools.mapNotNull { toMcpToolInfo(it) }
                    val resources = runCatching {
                        session.request("resources/list", buildJsonObject { })
                    }.getOrNull()?.let { extractResources(it) }.orEmpty()
                    val prompts = runCatching {
                        session.request("prompts/list", buildJsonObject { })
                    }.getOrNull()?.let { extractPrompts(it) }.orEmpty()
                    McpDiscoveryResult(
                        serverId = server.id,
                        serverLabel = server.name.ifBlank { server.type }.ifBlank { server.id },
                        tools = toolInfos,
                        resources = resources,
                        prompts = prompts,
                        capabilities = McpServerCapabilities(
                            tools = toolInfos.isNotEmpty(),
                            resources = resources.isNotEmpty(),
                            prompts = prompts.isNotEmpty()
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            logger.log(TAG, "MCP discover server=${server.id} failed: ${t.message}")
            null
        }
    }

    private fun toMcpToolInfo(tool: JsonObject): McpToolInfo? {
        val name = tool["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val description = tool["description"]?.jsonPrimitive?.contentOrNull
        val schema = tool["inputSchema"]?.jsonObject
        val schemaSummary = schema?.get("properties")?.jsonObject?.keys?.joinToString(", ") ?: ""
        return McpToolInfo(
            name = name,
            description = description?.takeIf { it.isNotBlank() },
            inputSchemaSummary = schemaSummary.takeIf { it.isNotBlank() }
        )
    }

    override suspend fun fetchContext(server: McpServerConfig, query: String): McpFetchResult {
        AppLogger.i(TAG, "[MCP] fetchContext START server=${server.id} type=${server.type} query=${query.take(80)}...")
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
                    AppLogger.i(TAG, "[MCP] session started, requesting tools/list...")
                    val toolsResponse = session.request(
                        method = "tools/list",
                        params = buildJsonObject { }
                    )

                    val tools = extractTools(toolsResponse)
                    AppLogger.i(
                        TAG,
                        "[MCP] tools/list count=${tools.size} names=${
                            tools.mapNotNull { it["name"]?.toString() }.take(5)
                        }"
                    )
                    if (tools.isEmpty()) {
                        AppLogger.w(TAG, "[MCP] FAIL no tools returned")
                        return@runMcpSession McpFetchResult.Failure("No tools returned by MCP server")
                    }

                    val attemptResult = callToolsForQuery(session, server, tools, query)
                    AppLogger.i(
                        TAG,
                        "[MCP] callToolsForQuery result: ${if (attemptResult is McpFetchResult.Success) "Success contentLen=${attemptResult.content.length}" else "Failure reason=${(attemptResult as McpFetchResult.Failure).reason}"}"
                    )
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
            AppLogger.e(
                TAG,
                "[MCP] fetchContext EXCEPTION server=${server.id} ${t.javaClass.simpleName}: ${t.message}",
                t
            )
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

    private fun isWindows(): Boolean =
        System.getProperty("os.name", "").lowercase(Locale.ROOT).contains("win")

    /**
     * On Windows, npx is a .cmd script and ProcessBuilder cannot run it directly.
     * Use cmd.exe /c npx ... so the command is executed by the shell.
     */
    private fun buildProcessCommand(server: McpServerConfig): List<String> {
        val command = server.command?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("MCP command is missing for server=${server.id}")
        val args = server.args ?: emptyList()
        if (!isWindows()) {
            return listOf(command) + args
        }
        val npxLike = command.lowercase(Locale.ROOT) == "npx" || command.lowercase(Locale.ROOT).endsWith("npx.cmd")
        return if (npxLike) {
            listOf("cmd.exe", "/c", command) + args
        } else {
            listOf(command) + args
        }
    }

    private suspend fun <T> runMcpSession(
        server: McpServerConfig,
        timeoutMs: Long,
        block: suspend (McpSession) -> T
    ): T {
        val processBuilder = ProcessBuilder(buildProcessCommand(server))
        server.env.orEmpty().forEach { (key, value) -> processBuilder.environment()[key] = value }
        server.workingDirectory?.takeIf { it.isNotBlank() }?.let { wd ->
            processBuilder.directory(java.io.File(wd).absoluteFile)
        }
        processBuilder.redirectErrorStream(false)
        val process = processBuilder.start()

        val output = process.outputStream
        val input = PushbackInputStream(process.inputStream, 1)
        val errors = process.errorStream
        val stderrRef = AtomicReference<String>("")
        val stderrReader = Thread {
            try {
                val bytes = errors.readBytes()
                if (bytes.isNotEmpty()) {
                    stderrRef.set(String(bytes, StandardCharsets.UTF_8).trim().take(2000))
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        val session = McpSession(input = input, output = output, timeoutMs = timeoutMs)

        try {
            initialize(session)
            return block(session)
        } catch (t: Throwable) {
            stderrReader.join(2000)
            val stderr = stderrRef.get()
            val enriched = if (stderr.isNotBlank()) {
                RuntimeException("${t.message ?: "unknown"}. Stderr: ${stderr.take(800)}", t)
            } else t
            throw enriched
        } finally {
            runCatching { output.close(); input.close(); errors.close() }
            stderrReader.join(1000)
            if (process.isAlive) {
                process.destroy()
                if (process.isAlive) process.destroyForcibly()
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
        server: McpServerConfig,
        tools: List<JsonObject>,
        query: String
    ): McpFetchResult {
        val fetchStrategy = fetchStrategySelector(server)
        val resolvedPreference = fetchStrategy.resolvePreference(
            session = SessionToolSession(session),
            tools = tools,
            query = query
        )
        AppLogger.i(TAG, "[MCP] callToolsForQuery resolvedPreference=$resolvedPreference")

        for (tool in fetchStrategy.prioritizeTools(tools)) {
            val toolName = tool["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val argumentCandidates = fetchStrategy.buildArgumentCandidates(tool, query, resolvedPreference)
            AppLogger.i(TAG, "[MCP] callToolsForQuery tool='$toolName' candidates=${argumentCandidates.size}")

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

                val errorField = response["error"]
                if (errorField != null) {
                    AppLogger.w(
                        TAG,
                        "[MCP] callToolsForQuery tool='$toolName' MCP protocol error: ${
                            errorField.toString().take(300)
                        }"
                    )
                    continue
                }

                val content = extractContent(response)
                val isMcpError = content.startsWith("MCP error") ||
                        content.contains("Invalid arguments for tool") ||
                        content.contains("invalid_type")
                if (isMcpError) {
                    AppLogger.w(
                        TAG,
                        "[MCP] callToolsForQuery tool='$toolName' args returned MCP error, trying next candidate. Error: ${
                            content.take(200)
                        }"
                    )
                    continue
                }
                if (content.isNotBlank()) {
                    AppLogger.i(TAG, "[MCP] callToolsForQuery tool='$toolName' SUCCESS contentLen=${content.length}")
                    return McpFetchResult.Success(content)
                }
            }
        }

        AppLogger.w(TAG, "[MCP] callToolsForQuery FAIL no tool returned usable content")
        return McpFetchResult.Failure("MCP server returned no usable content for query")
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

    private fun extractResources(response: JsonObject): List<McpResourceInfo> {
        val list = response["result"]?.jsonObject?.get("resources")?.jsonArray.orEmpty()
        return list.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val uri = obj["uri"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            McpResourceInfo(
                uri = uri,
                name = obj["name"]?.jsonPrimitive?.contentOrNull,
                description = obj["description"]?.jsonPrimitive?.contentOrNull,
                mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull
            )
        }
    }

    private fun extractPrompts(response: JsonObject): List<McpPromptInfo> {
        val list = response["result"]?.jsonObject?.get("prompts")?.jsonArray.orEmpty()
        return list.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            McpPromptInfo(
                name = name,
                description = obj["description"]?.jsonPrimitive?.contentOrNull
            )
        }
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

    private class SessionToolSession(private val session: McpSession) : StdioMcpToolSession {
        override suspend fun callTool(toolName: String, arguments: JsonObject): JsonObject? {
            return runCatching {
                session.request(
                    method = "tools/call",
                    params = buildJsonObject {
                        put("name", toolName)
                        put("arguments", arguments)
                    }
                )
            }.getOrNull()
        }
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
                runInterruptible(Dispatchers.IO) {
                    readResponseForRequestId(requestId)
                }
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

        /**
         * MCP stdio transport: messages are newline-delimited JSON (one message per line).
         * Spec: https://modelcontextprotocol.io/specification/2024-11-05/basic/transports
         */
        private fun writeMessage(payload: JsonObject) {
            val line = json.encodeToString(JsonObject.serializer(), payload) + "\n"
            output.write(line.toByteArray(StandardCharsets.UTF_8))
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

        /**
         * MCP stdio: one JSON-RPC message per line (newline-delimited).
         */
        private fun readMessage(): String? {
            val line = readLine(input) ?: return null
            val trimmed = line.trim()
            return if (trimmed.isBlank()) null else trimmed
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


