package org.bothubclient.infrastructure.docindex

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentFileReaderTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var reader: DocumentFileReader

    @BeforeEach
    fun setUp() {
        reader = DocumentFileReader()
    }

    @Test
    fun readDocuments_emptyDirectory_returnsEmptyList() {
        // Act
        val result = reader.readDocuments(tempDir.toString())

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun readDocuments_txtFile_isRead() {
        // Arrange
        val file = tempDir.resolve("notes.txt")
        file.writeText("Hello world")

        // Act
        val result = reader.readDocuments(tempDir.toString())

        // Assert
        assertEquals(1, result.size)
        assertEquals("Hello world", result[0].content)
        assertEquals("notes.txt", result[0].fileName)
        assertEquals(".txt", result[0].extension)
    }

    @Test
    fun readDocuments_mdFile_isRead() {
        // Arrange
        val file = tempDir.resolve("readme.md")
        file.writeText("# Title\nContent")

        // Act
        val result = reader.readDocuments(tempDir.toString())

        // Assert
        assertEquals(1, result.size)
        assertEquals("# Title\nContent", result[0].content)
        assertEquals(".md", result[0].extension)
    }

    @Test
    fun readDocuments_unsupportedExtension_isSkipped() {
        // Arrange
        tempDir.resolve("image.png").createFile()
        tempDir.resolve("code.java").writeText("public class Foo {}")
        tempDir.resolve("data.json").writeText("{}")
        tempDir.resolve("valid.txt").writeText("valid content")

        // Act
        val result = reader.readDocuments(tempDir.toString())

        // Assert
        assertEquals(1, result.size)
        assertEquals("valid.txt", result[0].fileName)
    }

    @Test
    fun readDocuments_recursiveSubdirectories_findsFiles() {
        // Arrange
        val subDir = tempDir.resolve("sub").resolve("deep").createDirectories()
        subDir.resolve("deep.txt").writeText("deep content")
        tempDir.resolve("root.md").writeText("root content")

        // Act
        val result = reader.readDocuments(tempDir.toString())

        // Assert
        assertEquals(2, result.size)
        val fileNames = result.map { it.fileName }.toSet()
        assertTrue(fileNames.contains("deep.txt"))
        assertTrue(fileNames.contains("root.md"))
    }

    @Test
    fun readDocuments_nonExistentDirectory_returnsEmptyOrThrows() {
        // Arrange
        val nonExistent = tempDir.resolve("does_not_exist").toString()

        // Act & Assert
        // Path.of(directoryPath).toRealPath() throws if path does not exist
        try {
            val result = reader.readDocuments(nonExistent)
            // If it somehow returns, should be empty
            assertTrue(result.isEmpty())
        } catch (_: java.nio.file.NoSuchFileException) {
            // Expected: toRealPath() throws for non-existent path
        }
    }

    @Test
    fun readDocuments_multipleAllowedFiles_allRead() {
        // Arrange
        tempDir.resolve("a.txt").writeText("aaa")
        tempDir.resolve("b.txt").writeText("bbb")
        tempDir.resolve("c.md").writeText("ccc")

        // Act
        val result = reader.readDocuments(tempDir.toString())

        // Assert
        assertEquals(3, result.size)
    }

    @Test
    fun readDocuments_pathInResultIsAbsolute() {
        // Arrange
        tempDir.resolve("test.txt").writeText("content")

        // Act
        val result = reader.readDocuments(tempDir.toString())

        // Assert
        assertEquals(1, result.size)
        assertTrue(Path.of(result[0].path).isAbsolute)
    }

    @Test
    fun readDocuments_emptyFileContent_isStillIncluded() {
        // Arrange
        tempDir.resolve("empty.txt").writeText("")

        // Act
        val result = reader.readDocuments(tempDir.toString())

        // Assert
        assertEquals(1, result.size)
        assertEquals("", result[0].content)
    }
}
