package org.bothubclient.infrastructure.mcp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for MCP Context7 flow: extract library name from user query (e.g. "Посмотри документацию по Kotlinx.coroutines"),
 * library name variants, and parse library ID from resolve-library-id response.
 * Run with: ./gradlew test --tests "org.bothubclient.infrastructure.mcp.Context7QueryHelperTest"
 */
class Context7QueryHelperTest {

    @Test
    fun `extractLibraryName from exact user query Посмотри документацию по Kotlinx dot coroutines`() {
        val query = "Посмотри документацию по Kotlinx.coroutines"
        val name = Context7QueryHelper.extractLibraryName(query)
        assertEquals("Kotlinx.coroutines", name)
    }

    @Test
    fun `extractLibraryName from query without по - Посмотри документацию Kotlinx dot coroutines`() {
        val query = "Посмотри документацию Kotlinx.coroutines"
        val name = Context7QueryHelper.extractLibraryName(query)
        assertEquals("Kotlinx.coroutines", name)
    }

    @Test
    fun `extractLibraryName from query with trailing dot`() {
        val query = "Посмотри документацию по Kotlinx.coroutines."
        val name = Context7QueryHelper.extractLibraryName(query)
        assertEquals("Kotlinx.coroutines.", name)
    }

    @Test
    fun `extractLibraryName документация по prefix`() {
        assertEquals("Kotlinx.coroutines", Context7QueryHelper.extractLibraryName("Документация по Kotlinx.coroutines"))
    }

    @Test
    fun `extractLibraryName посмотри документацию о prefix`() {
        assertEquals(
            "Kotlinx.coroutines",
            Context7QueryHelper.extractLibraryName("Посмотри документацию о Kotlinx.coroutines")
        )
    }

    @Test
    fun `libraryNameVariants for Kotlinx dot coroutines includes Kotlin Coroutines and hyphen form`() {
        val variants = Context7QueryHelper.libraryNameVariants("Kotlinx.coroutines")
        assertTrue(variants.contains("Kotlinx.coroutines"))
        assertTrue(variants.contains("Kotlin Coroutines"))
        assertTrue(variants.contains("kotlinx-coroutines"))
    }

    @Test
    fun `full flow extract name then variants for user query`() {
        val query = "Посмотри документацию по Kotlinx.coroutines"
        val name = Context7QueryHelper.extractLibraryName(query)
        assertEquals("Kotlinx.coroutines", name)
        val variants = Context7QueryHelper.libraryNameVariants(name)
        assertTrue(variants.size >= 2)
        assertTrue(variants.any { it.contains("coroutines", ignoreCase = true) })
    }

    @Test
    fun `parseLibraryIdFromResolveResponse parses JSON array from Context7 API`() {
        val json = """[{"id": "/Kotlin/kotlinx.coroutines", "name": "kotlinx.coroutines"}]"""
        val id = Context7QueryHelper.parseLibraryIdFromResolveResponse(json)
        assertEquals("/Kotlin/kotlinx.coroutines", id)
    }

    @Test
    fun `parseLibraryIdFromResolveResponse parses single JSON object with id`() {
        val json = """{"id": "/Kotlin/kotlinx.coroutines", "name": "kotlinx.coroutines"}"""
        val id = Context7QueryHelper.parseLibraryIdFromResolveResponse(json)
        assertEquals("/Kotlin/kotlinx.coroutines", id)
    }

    @Test
    fun `parseLibraryIdFromResolveResponse parses object with libraries array`() {
        val json = """{"libraries": [{"id": "/Kotlin/kotlinx.coroutines"}]}"""
        val id = Context7QueryHelper.parseLibraryIdFromResolveResponse(json)
        assertEquals("/Kotlin/kotlinx.coroutines", id)
    }

    @Test
    fun `parseLibraryIdFromResolveResponse returns null for empty or invalid`() {
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse(""))
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("[]"))
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("no id here"))
    }

    @Test
    fun `parseLibraryIdFromResolveResponse extracts id from MCP text fallback`() {
        val text = """Context7-compatible library ID: /Kotlin/kotlinx.coroutines"""
        val id = Context7QueryHelper.parseLibraryIdFromResolveResponse(text)
        assertEquals("/Kotlin/kotlinx.coroutines", id)
    }

    @Test
    fun `parseLibraryIdFromResolveResponse regex fallback for slash path`() {
        val text = """Something /Kotlin/kotlinx.coroutines end"""
        val id = Context7QueryHelper.parseLibraryIdFromResolveResponse(text)
        assertEquals("/Kotlin/kotlinx.coroutines", id)
    }

    // --- Runtime robustness: inputs that can come from real MCP / network and must not throw ---

    @Test
    fun `parseLibraryIdFromResolveResponse does not throw on empty or blank`() {
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse(""))
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("   "))
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("\t\n"))
    }

    @Test
    fun `parseLibraryIdFromResolveResponse does not throw on literal string null`() {
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("null"))
    }

    @Test
    fun `parseLibraryIdFromResolveResponse does not throw on empty JSON object or array`() {
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("{}"))
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("[]"))
    }

    @Test
    fun `parseLibraryIdFromResolveResponse does not throw on error-like JSON`() {
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("""{"error": "not found"}"""))
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("""{"error": true, "message": "timeout"}"""))
    }

    @Test
    fun `parseLibraryIdFromResolveResponse does not throw on array of null or empty object`() {
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("[null]"))
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("[{}]"))
    }

    @Test
    fun `parseLibraryIdFromResolveResponse does not throw when id is number or invalid`() {
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("""[{"id": 123}]"""))
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("""[{"id": "no-leading-slash"}]"""))
    }

    @Test
    fun `parseLibraryIdFromResolveResponse does not throw on non-JSON text`() {
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("not json at all"))
        assertNull(Context7QueryHelper.parseLibraryIdFromResolveResponse("<html><body>error</body></html>"))
    }

    @Test
    fun `extractLibraryName does not throw on empty or blank query`() {
        assertEquals("", Context7QueryHelper.extractLibraryName(""))
        assertTrue(Context7QueryHelper.extractLibraryName("   ").isBlank())
    }

    @Test
    fun `extractLibraryName does not throw on very long query`() {
        val longQuery = "документация по " + "x".repeat(500)
        val name = Context7QueryHelper.extractLibraryName(longQuery)
        assertTrue(name.length <= 80)
    }

    @Test
    fun `libraryNameVariants does not throw on empty or blank`() {
        assertTrue(Context7QueryHelper.libraryNameVariants("").isEmpty())
        assertTrue(Context7QueryHelper.libraryNameVariants("   ").isEmpty())
    }
}
