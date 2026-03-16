package org.bothubclient.infrastructure.docindex

import org.bothubclient.config.DocumentIndexConfig
import org.bothubclient.domain.docindex.ChunkMetadata
import org.bothubclient.domain.docindex.ChunkingStrategy
import org.bothubclient.domain.docindex.ChunkingStrategyType
import org.bothubclient.domain.docindex.DocumentChunk
import java.util.UUID

class StructuralChunkingStrategy(
    private val maxChunkSizeTokens: Int = DocumentIndexConfig.STRUCTURAL_MAX_CHUNK_SIZE
) : ChunkingStrategy {

    override val type: ChunkingStrategyType = ChunkingStrategyType.STRUCTURAL
    override val displayName: String = "Structural (by headings)"

    private val markdownHeaderRegex = Regex("^#{1,6}\\s+", RegexOption.MULTILINE)

    override fun chunk(content: String, source: String, title: String): List<DocumentChunk> {
        if (content.isBlank()) return emptyList()

        val isMarkdown = source.endsWith(".md", ignoreCase = true)
        val sections = if (isMarkdown) splitByMarkdownHeaders(content) else splitByDoubleNewlines(content)

        val chunks = mutableListOf<DocumentChunk>()
        var chunkIndex = 0
        var charOffset = 0

        for ((sectionHeader, sectionContent) in sections) {
            val fullSection = if (sectionHeader.isNotBlank()) "$sectionHeader\n$sectionContent" else sectionContent
            val sectionTokens = fullSection.length / 4

            if (sectionTokens <= maxChunkSizeTokens) {
                if (fullSection.isNotBlank()) {
                    chunks.add(
                        DocumentChunk(
                            chunkId = UUID.randomUUID().toString(),
                            content = fullSection,
                            metadata = ChunkMetadata(
                                source = source,
                                title = title,
                                section = sectionHeader.trim(),
                                chunkIndex = chunkIndex,
                                charOffset = charOffset,
                                tokenCount = sectionTokens
                            )
                        )
                    )
                    chunkIndex++
                }
            } else {
                val subChunks = splitLargeSection(
                    fullSection, source, title, sectionHeader.trim(), chunkIndex, charOffset
                )
                chunks.addAll(subChunks)
                chunkIndex += subChunks.size
            }

            charOffset += fullSection.length + 1
        }

        return chunks
    }

    private fun splitByMarkdownHeaders(content: String): List<Pair<String, String>> {
        val sections = mutableListOf<Pair<String, String>>()
        val matches = markdownHeaderRegex.findAll(content).toList()

        if (matches.isEmpty()) {
            sections.add("" to content)
            return sections
        }

        if (matches.first().range.first > 0) {
            val preamble = content.substring(0, matches.first().range.first).trim()
            if (preamble.isNotBlank()) {
                sections.add("" to preamble)
            }
        }

        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else content.length
            val sectionText = content.substring(start, end)
            val firstNewline = sectionText.indexOf('\n')
            if (firstNewline > 0) {
                val header = sectionText.substring(0, firstNewline).trim()
                val body = sectionText.substring(firstNewline + 1).trim()
                sections.add(header to body)
            } else {
                sections.add(sectionText.trim() to "")
            }
        }

        return sections
    }

    private fun splitByDoubleNewlines(content: String): List<Pair<String, String>> {
        return content.split(Regex("\n\\s*\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { "" to it }
    }

    private fun splitLargeSection(
        text: String,
        source: String,
        title: String,
        section: String,
        startChunkIndex: Int,
        startCharOffset: Int
    ): List<DocumentChunk> {
        val chunkSizeChars = maxChunkSizeTokens * 4
        val overlapChars = DocumentIndexConfig.DEFAULT_OVERLAP * 4
        val stepChars = (chunkSizeChars - overlapChars).coerceAtLeast(1)

        val chunks = mutableListOf<DocumentChunk>()
        var offset = 0
        var subIndex = 0

        while (offset < text.length) {
            val end = (offset + chunkSizeChars).coerceAtMost(text.length)
            val chunkText = text.substring(offset, end)

            if (chunkText.isNotBlank()) {
                chunks.add(
                    DocumentChunk(
                        chunkId = UUID.randomUUID().toString(),
                        content = chunkText,
                        metadata = ChunkMetadata(
                            source = source,
                            title = title,
                            section = section,
                            chunkIndex = startChunkIndex + subIndex,
                            charOffset = startCharOffset + offset,
                            tokenCount = chunkText.length / 4
                        )
                    )
                )
                subIndex++
            }

            offset += stepChars
        }

        return chunks
    }
}
