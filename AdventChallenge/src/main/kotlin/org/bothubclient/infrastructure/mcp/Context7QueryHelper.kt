package org.bothubclient.infrastructure.mcp

import kotlinx.serialization.json.*
import org.bothubclient.infrastructure.logging.AppLogger
import java.util.*

/**
 * Testable helper for Context7 MCP: extract library name from user query,
 * variants for resolve-library-id, and parse library ID from API/MCP response.
 * Used by [StdioMcpClient]; unit tests can call this without starting the MCP process.
 */
internal object Context7QueryHelper {

    private const val TAG = "Context7QueryHelper"
    private val json = Json { ignoreUnknownKeys = true }

    private val libraryNamePrefixes = listOf(
        "найди информацию о ",
        "find information about ",
        "documentation for ",
        "документация по ",
        "информация о ",
        "information about ",
        "уточним документацию ",
        "давай уточним документацию ",
        "get docs for ",
        "show docs for ",
        "покажи документацию ",
        "расскажи о ",
        "посмотри документацию о ",
        "посмотри документацию по ",
        "посмотри документацию ",
        "look up documentation for ",
        "look at documentation for "
    )

    /**
     * Extracts a short library name from the user query for resolve-library-id.
     * E.g. "Посмотри документацию по Kotlinx.coroutines" -> "Kotlinx.coroutines"
     */
    fun extractLibraryName(query: String): String {
        val trimmed = query.trim().take(120)
        val lower = trimmed.lowercase(Locale.ROOT)
        var name = trimmed
        for (p in libraryNamePrefixes) {
            if (lower.startsWith(p)) {
                name = trimmed.substring(p.length).trim()
                break
            }
        }
        name = name.replace(Regex("\\s+через\\s+context.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+via\\s+context.*$", RegexOption.IGNORE_CASE), "")
            .trim()
        val out = name.ifBlank { trimmed }.take(80)
        AppLogger.d(TAG, "extractLibraryName query=${query.take(50)}... -> '$out'")
        return out
    }

    /**
     * Alternative library names to try for resolve-library-id when the first attempt returns no ID.
     */
    fun libraryNameVariants(name: String): List<String> {
        val n = name.trim()
        if (n.isBlank()) return emptyList()
        val variants = mutableListOf(n)
        when (n.lowercase()) {
            "kotlinx.coroutines", "kotlinx-coroutines" -> {
                if (!variants.contains("Kotlin Coroutines")) variants.add("Kotlin Coroutines")
                if (!variants.contains("kotlinx-coroutines")) variants.add("kotlinx-coroutines")
                if (!variants.contains("kotlinx.coroutines")) variants.add("kotlinx.coroutines")
            }

            "kotlin coroutines" -> {
                variants.add("kotlinx.coroutines")
                variants.add("kotlinx-coroutines")
            }

            else -> {
                val withHyphens = n.replace(".", "-")
                if (withHyphens != n && !variants.contains(withHyphens)) variants.add(withHyphens)
            }
        }
        val out = variants.distinct()
        AppLogger.d(TAG, "libraryNameVariants name='$name' -> $out")
        return out
    }

    /**
     * Parses Context7-compatible library ID from resolve-library-id response.
     * API returns JSON array [{ "id": "/owner/repo", ... }]; MCP may return that or plain text.
     */
    fun parseLibraryIdFromResolveResponse(content: String): String? {
        val trimmed = content.trim()
        runCatching {
            val el = json.parseToJsonElement(trimmed)
            when (el) {
                is JsonArray -> el.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
                is JsonObject -> el["id"]?.jsonPrimitive?.contentOrNull
                    ?: el["libraries"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull

                else -> null
            }?.takeIf { it.startsWith("/") }?.let { return it }
        }
        val idPattern = Regex("""(?:Context7-compatible library ID|library ID)\s*:\s*(/\S+)""", RegexOption.IGNORE_CASE)
        val match = idPattern.find(trimmed) ?: Regex("""(/\w+/\S+)""").find(trimmed)
        val out = match?.groupValues?.getOrNull(1)?.takeIf { it.startsWith("/") }
        AppLogger.d(TAG, "parseLibraryIdFromResolveResponse contentLen=${content.length} -> '$out'")
        return out
    }
}
