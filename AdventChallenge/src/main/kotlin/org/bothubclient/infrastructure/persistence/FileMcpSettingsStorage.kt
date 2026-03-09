package org.bothubclient.infrastructure.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bothubclient.domain.entity.McpServerConfig
import java.nio.file.Path
import kotlin.io.path.*

class FileMcpSettingsStorage(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    },
    baseDir: Path = Path.of(System.getProperty("user.home"), ".bothubclient")
) {
    private val settingsDir: Path = runCatching {
        baseDir.apply { createDirectories() }
    }.getOrElse {
        Path.of(System.getProperty("java.io.tmpdir"), "bothubclient").apply {
            createDirectories()
        }
    }

    private val serversFile: Path = settingsDir / "mcp_servers.json"

    suspend fun loadServers(): List<McpServerConfig> = withContext(Dispatchers.IO) {
        if (!serversFile.exists()) {
            return@withContext emptyList()
        }

        runCatching {
            val content = serversFile.readText()
            if (content.isBlank()) {
                return@withContext emptyList()
            }
            json.decodeFromString<List<McpServerConfig>>(content)
        }.getOrElse { e ->
            println("[McpSettingsStorage] Failed to load MCP servers: ${e.message}")
            emptyList()
        }
    }

    suspend fun saveServers(servers: List<McpServerConfig>) = withContext(Dispatchers.IO) {
        runCatching {
            val content = json.encodeToString(servers)
            serversFile.writeText(content)
        }.getOrElse { e ->
            println("[McpSettingsStorage] Failed to save MCP servers: ${e.message}")
            throw e
        }
    }
}
