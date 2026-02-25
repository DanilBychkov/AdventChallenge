package org.bothubclient.infrastructure.persistence

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.MessageMetrics
import org.bothubclient.domain.entity.MessageRole
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileChatHistoryStorageTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var storage: FileChatHistoryStorage
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @BeforeEach
    fun setup() {
        storage = FileChatHistoryStorage(
            json = json,
            baseDir = tempDir.resolve("chat_history")
        )
    }

    @Test
    fun loadHistory_should_return_empty_list_when_file_does_not_exist() = runTest {
        val sessionId = "non-existent-session"
        val result = storage.loadHistory(sessionId)
        assertTrue(result.isEmpty())
    }

    @Test
    fun loadHistory_should_return_empty_list_when_file_is_empty() = runTest {
        val sessionId = "empty-session"
        tempDir.resolve("chat_history").createDirectories()
            .resolve("empty-session.json").writeText("")
        val result = storage.loadHistory(sessionId)
        assertTrue(result.isEmpty())
    }

    @Test
    fun loadHistory_should_parse_valid_json_file() = runTest {
        val sessionId = "valid-session"
        val jsonContent = """
            {
                "version": 1,
                "sessionId": "valid-session",
                "messages": [
                    {
                        "role": "USER",
                        "content": "Hello",
                        "timestamp": "10:00",
                        "metrics": null
                    },
                    {
                        "role": "ASSISTANT",
                        "content": "Hi there!",
                        "timestamp": "10:01",
                        "metrics": {
                            "promptTokens": 10,
                            "completionTokens": 5,
                            "totalTokens": 15,
                            "responseTimeMs": 1000,
                            "cost": 0.001
                        }
                    }
                ],
                "createdAt": "2024-01-01T10:00:00Z",
                "updatedAt": "2024-01-01T10:01:00Z"
            }
        """.trimIndent()

        tempDir.resolve("chat_history").createDirectories()
            .resolve("valid-session.json").writeText(jsonContent)

        val result = storage.loadHistory(sessionId)

        assertEquals(2, result.size)
        assertEquals(MessageRole.USER, result[0].role)
        assertEquals("Hello", result[0].content)
        assertEquals(MessageRole.ASSISTANT, result[1].role)
        assertEquals("Hi there!", result[1].content)
        assertNotNull(result[1].metrics)
        assertEquals(10, result[1].metrics!!.promptTokens)
    }

    @Test
    fun loadHistory_should_return_empty_list_on_malformed_json() = runTest {
        val sessionId = "malformed-session"
        tempDir.resolve("chat_history").createDirectories()
            .resolve("malformed-session.json").writeText("{ invalid json }")
        val result = storage.loadHistory(sessionId)
        assertTrue(result.isEmpty())
    }

    @Test
    fun loadHistory_should_sanitize_session_id_for_file_path() = runTest {
        val sessionId = "test/session@123#456"
        storage.saveHistory(sessionId, listOf(Message.user("Test")))
        val files = tempDir.resolve("chat_history").listDirectoryEntries()
        assertTrue(files.any { it.name == "test_session_123_456.json" })
    }

    @Test
    fun saveHistory_should_create_new_file() = runTest {
        val sessionId = "new-session"
        val messages = listOf(
            Message.user("Hello"),
            Message.assistant("Hi!")
        )
        storage.saveHistory(sessionId, messages)
        val file = tempDir.resolve("chat_history").resolve("new-session.json")
        assertTrue(file.exists())
        val loaded = storage.loadHistory(sessionId)
        assertEquals(2, loaded.size)
    }

    @Test
    fun saveHistory_should_overwrite_existing_file() = runTest {
        val sessionId = "overwrite-session"
        storage.saveHistory(sessionId, listOf(Message.user("First")))
        storage.saveHistory(
            sessionId, listOf(
                Message.user("Second"),
                Message.assistant("Response")
            )
        )
        val loaded = storage.loadHistory(sessionId)
        assertEquals(2, loaded.size)
        assertEquals("Second", loaded[0].content)
    }

    @Test
    fun saveHistory_should_save_message_with_metrics() = runTest {
        val sessionId = "metrics-session"
        val metrics = MessageMetrics(
            promptTokens = 100,
            completionTokens = 50,
            totalTokens = 150,
            responseTimeMs = 2000,
            cost = 0.05
        )
        val messages = listOf(
            Message.user("Question"),
            Message.assistant("Answer", metrics)
        )
        storage.saveHistory(sessionId, messages)
        val loaded = storage.loadHistory(sessionId)
        assertEquals(2, loaded.size)
        val loadedMetrics = loaded[1].metrics
        assertNotNull(loadedMetrics)
        assertEquals(100, loadedMetrics.promptTokens)
        assertEquals(50, loadedMetrics.completionTokens)
        assertEquals(0.05, loadedMetrics.cost)
    }

    @Test
    fun deleteHistory_should_remove_existing_file() = runTest {
        val sessionId = "to-delete"
        storage.saveHistory(sessionId, listOf(Message.user("Test")))
        storage.deleteHistory(sessionId)
        assertFalse(tempDir.resolve("chat_history").resolve("$sessionId.json").exists())
    }

    @Test
    fun deleteHistory_should_not_throw_when_file_does_not_exist() = runTest {
        val sessionId = "non-existent"
        try {
            storage.deleteHistory(sessionId)
            assertTrue(true)
        } catch (e: Exception) {
            throw AssertionError("Should not throw exception", e)
        }
    }

    @Test
    fun listSessions_should_return_all_session_ids() = runTest {
        storage.saveHistory("session1", listOf(Message.user("1")))
        storage.saveHistory("session2", listOf(Message.user("2")))
        storage.saveHistory("session3", listOf(Message.user("3")))
        val sessions = storage.listSessions()
        assertEquals(3, sessions.size)
        assertTrue(sessions.containsAll(listOf("session1", "session2", "session3")))
    }

    @Test
    fun listSessions_should_return_empty_list_when_no_sessions_exist() = runTest {
        val sessions = storage.listSessions()
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun listSessions_should_ignore_non_json_files() = runTest {
        storage.saveHistory("valid", listOf(Message.user("test")))
        tempDir.resolve("chat_history").resolve("readme.txt").writeText("info")
        val sessions = storage.listSessions()
        assertEquals(1, sessions.size)
        assertEquals("valid", sessions.first())
    }

    @Test
    fun should_save_and_load_all_message_types() = runTest {
        val sessionId = "all-types"
        val messages = listOf(
            Message.user("User message"),
            Message.assistant("Assistant message"),
            Message.system("System message"),
            Message.error("Error message")
        )
        storage.saveHistory(sessionId, messages)
        val loaded = storage.loadHistory(sessionId)
        assertEquals(4, loaded.size)
        assertEquals(MessageRole.USER, loaded[0].role)
        assertEquals(MessageRole.ASSISTANT, loaded[1].role)
        assertEquals(MessageRole.SYSTEM, loaded[2].role)
        assertEquals(MessageRole.ERROR, loaded[3].role)
    }
}
