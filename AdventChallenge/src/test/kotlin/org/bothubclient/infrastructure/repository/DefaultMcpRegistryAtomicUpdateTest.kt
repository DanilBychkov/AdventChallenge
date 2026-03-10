package org.bothubclient.infrastructure.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.bothubclient.config.McpPresets
import org.bothubclient.domain.entity.McpServerConfig
import org.bothubclient.infrastructure.persistence.FileMcpSettingsStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

class DefaultMcpRegistryAtomicUpdateTest {

    @TempDir
    lateinit var tempDir: Path

    private val presetCount: Int get() = McpPresets.getAllPresets().size

    private fun createTestStorage(): FileMcpSettingsStorage {
        val testStorageDir = tempDir.resolve("bothubclient_test_${System.nanoTime()}")
        testStorageDir.createDirectories()
        return FileMcpSettingsStorage(baseDir = testStorageDir)
    }

    @Test
    fun `runAtomicUpdate with concurrent updates preserves both changes`() = runTest {
        val storage = createTestStorage()
        val registry = DefaultMcpRegistry(storage)

        // Clean up any existing file
        val serversFile = tempDir.resolve("bothubclient_test_${tempDir.fileName}").resolve("mcp_servers.json")
        if (serversFile.exists()) {
            serversFile.deleteIfExists()
        }

        // Initial server setup
        val server1 = McpServerConfig(
            id = "server-1",
            name = "Server 1",
            type = "test-type-1",
            enabled = true,
            forceUsage = false
        )
        val server2 = McpServerConfig(
            id = "server-2",
            name = "Server 2",
            type = "test-type-2",
            enabled = true,
            forceUsage = false
        )

        // Initialize with empty list
        registry.runAtomicUpdate { _ ->
            Pair(emptyList(), Unit)
        }

        // Launch two concurrent updates
        val deferred1 = async {
            registry.runAtomicUpdate { list ->
                val updated = list + server1
                Pair(updated, "update1-done")
            }
        }
        val deferred2 = async {
            registry.runAtomicUpdate { list ->
                val updated = list + server2
                Pair(updated, "update2-done")
            }
        }

        // Wait for both updates to complete
        val results = awaitAll(deferred1, deferred2)
        assertEquals(listOf("update1-done", "update2-done"), results)

        // Verify both servers are present (no lost update). getAll() returns presets + custom servers.
        val finalList = registry.getAll()
        assertEquals(presetCount + 2, finalList.size, "expected presets + server-1 + server-2")
        assertTrue(finalList.any { it.id == "server-1" }, "server-1 should be present")
        assertTrue(finalList.any { it.id == "server-2" }, "server-2 should be present")
    }

    @Test
    fun `runAtomicUpdate preserves sequential consistency`() = runTest {
        val storage = createTestStorage()
        val registry = DefaultMcpRegistry(storage)

        val server1 = McpServerConfig(
            id = "server-1",
            name = "Server 1",
            type = "test-type",
            enabled = true,
            forceUsage = false
        )
        val server2 = McpServerConfig(
            id = "server-2",
            name = "Server 2",
            type = "test-type",
            enabled = true,
            forceUsage = false
        )

        // First update
        registry.runAtomicUpdate { _ ->
            Pair(listOf(server1), "first")
        }

        // Second update
        val result = registry.runAtomicUpdate { list ->
            val updated = list + server2
            Pair(updated, "second")
        }

        assertEquals("second", result)

        val finalList = registry.getAll()
        assertEquals(presetCount + 2, finalList.size, "expected presets + server-1 + server-2")
        assertTrue(finalList.any { it.id == "server-1" })
        assertTrue(finalList.any { it.id == "server-2" })
    }

    @Test
    fun `runAtomicUpdate returns custom result type`() = runTest {
        val storage = createTestStorage()
        val registry = DefaultMcpRegistry(storage)

        val server = McpServerConfig(
            id = "test-server",
            name = "Test Server",
            type = "test-type",
            enabled = true,
            forceUsage = false
        )

        // Test that we can return a custom result type
        val result: Int = registry.runAtomicUpdate { list ->
            val updated = list + server
            Pair(updated, 42)
        }

        assertEquals(42, result)
        assertEquals(presetCount + 1, registry.getAll().size, "expected presets + test-server")
    }
}
