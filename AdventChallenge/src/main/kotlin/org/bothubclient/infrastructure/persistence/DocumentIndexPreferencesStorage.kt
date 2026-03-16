package org.bothubclient.infrastructure.persistence

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.*

@Serializable
data class DocumentIndexPreferences(
    val directory: String = "",
    val projectHash: String = "",
    val enabled: Boolean = false
)

class DocumentIndexPreferencesStorage(
    baseDir: Path = Path.of(System.getProperty("user.home"), ".bothubclient")
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefsFile: Path = baseDir.resolve("doc-index-prefs.json")

    fun load(): DocumentIndexPreferences {
        if (!prefsFile.exists()) return DocumentIndexPreferences()
        return runCatching {
            json.decodeFromString<DocumentIndexPreferences>(prefsFile.readText())
        }.getOrDefault(DocumentIndexPreferences())
    }

    fun save(prefs: DocumentIndexPreferences) {
        runCatching { prefsFile.writeText(json.encodeToString(prefs)) }
    }
}
