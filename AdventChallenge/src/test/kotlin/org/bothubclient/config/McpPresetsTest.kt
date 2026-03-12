package org.bothubclient.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class McpPresetsTest {

    @Test
    fun `getAllPresets includes search-mcp preset`() {
        val presets = McpPresets.getAllPresets()
        assertTrue(presets.any { it.id == "search-mcp" })
    }

    @Test
    fun `getAllPresets includes summarize-mcp preset`() {
        val presets = McpPresets.getAllPresets()
        assertTrue(presets.any { it.id == "summarize-mcp" })
    }

    @Test
    fun `getAllPresets includes save-mcp preset`() {
        val presets = McpPresets.getAllPresets()
        assertTrue(presets.any { it.id == "save-mcp" })
    }

    @Test
    fun `search-mcp preset has correct workingDirectory and command`() {
        val preset = McpPresets.getPresetById("search-mcp")
        assertNotNull(preset)
        assertEquals("node", preset!!.command)
        assertEquals(listOf("dist/index.js"), preset.args)
        assertEquals("mcp-servers/search-mcp", preset.workingDirectory)
    }

    @Test
    fun `summarize-mcp preset has correct configuration`() {
        val preset = McpPresets.getPresetById("summarize-mcp")
        assertNotNull(preset)
        assertEquals("mcp-servers/summarize-mcp", preset!!.workingDirectory)
    }

    @Test
    fun `save-mcp preset has correct configuration`() {
        val preset = McpPresets.getPresetById("save-mcp")
        assertNotNull(preset)
        assertEquals("mcp-servers/save-mcp", preset!!.workingDirectory)
    }

    @Test
    fun `all new presets are enabled by default`() {
        val presets = McpPresets.getAllPresets()
        val searchPreset = presets.find { it.id == "search-mcp" }
        val summarizePreset = presets.find { it.id == "summarize-mcp" }
        val savePreset = presets.find { it.id == "save-mcp" }
        assertTrue(searchPreset!!.enabled)
        assertTrue(summarizePreset!!.enabled)
        assertTrue(savePreset!!.enabled)
    }

    @Test
    fun `all new presets have forceUsage false`() {
        val presets = McpPresets.getAllPresets()
        val searchPreset = presets.find { it.id == "search-mcp" }
        val summarizePreset = presets.find { it.id == "summarize-mcp" }
        val savePreset = presets.find { it.id == "save-mcp" }
        assertTrue(!searchPreset!!.forceUsage)
        assertTrue(!summarizePreset!!.forceUsage)
        assertTrue(!savePreset!!.forceUsage)
    }

    @Test
    fun `getPresetById returns correct preset for each new id`() {
        assertEquals("search-mcp", McpPresets.getPresetById("search-mcp")!!.id)
        assertEquals("summarize-mcp", McpPresets.getPresetById("summarize-mcp")!!.id)
        assertEquals("save-mcp", McpPresets.getPresetById("save-mcp")!!.id)
        assertNotNull(McpPresets.getPresetById("search-mcp"))
        assertNotNull(McpPresets.getPresetById("summarize-mcp"))
        assertNotNull(McpPresets.getPresetById("save-mcp"))
    }

    @Test
    fun `preset ids are unique across all presets`() {
        val presets = McpPresets.getAllPresets()
        val ids = presets.map { it.id }
        val uniqueIds = ids.toSet()
        assertEquals(ids.size, uniqueIds.size, "All preset ids should be unique")
    }
}
