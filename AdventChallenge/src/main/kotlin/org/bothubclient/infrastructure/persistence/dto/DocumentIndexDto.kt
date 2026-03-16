package org.bothubclient.infrastructure.persistence.dto

import kotlinx.serialization.Serializable
import org.bothubclient.domain.docindex.ChunkMetadata
import org.bothubclient.domain.docindex.DocumentChunk
import org.bothubclient.domain.docindex.StoredDocumentIndex

@Serializable
data class DocumentIndexFileDto(
    val version: Int = 1,
    val model: String,
    val dimensions: Int,
    val strategy: String,
    val createdAt: String,
    val sourceDirectory: String,
    val chunks: List<DocumentChunkDto> = emptyList()
)

@Serializable
data class DocumentChunkDto(
    val chunkId: String,
    val content: String,
    val metadata: ChunkMetadataDto,
    val embedding: List<Float>? = null
)

@Serializable
data class ChunkMetadataDto(
    val source: String,
    val title: String,
    val section: String,
    val chunkIndex: Int,
    val charOffset: Int,
    val tokenCount: Int
)

fun StoredDocumentIndex.toDto(): DocumentIndexFileDto = DocumentIndexFileDto(
    version = version,
    model = model,
    dimensions = dimensions,
    strategy = strategy,
    createdAt = createdAt,
    sourceDirectory = sourceDirectory,
    chunks = chunks.map { it.toDto() }
)

fun DocumentChunk.toDto(): DocumentChunkDto = DocumentChunkDto(
    chunkId = chunkId,
    content = content,
    metadata = metadata.toDto(),
    embedding = embedding
)

fun ChunkMetadata.toDto(): ChunkMetadataDto = ChunkMetadataDto(
    source = source,
    title = title,
    section = section,
    chunkIndex = chunkIndex,
    charOffset = charOffset,
    tokenCount = tokenCount
)

fun DocumentIndexFileDto.toDomain(): StoredDocumentIndex = StoredDocumentIndex(
    version = version,
    model = model,
    dimensions = dimensions,
    strategy = strategy,
    createdAt = createdAt,
    sourceDirectory = sourceDirectory,
    chunks = chunks.map { it.toDomain() }
)

fun DocumentChunkDto.toDomain(): DocumentChunk = DocumentChunk(
    chunkId = chunkId,
    content = content,
    metadata = metadata.toDomain(),
    embedding = embedding
)

fun ChunkMetadataDto.toDomain(): ChunkMetadata = ChunkMetadata(
    source = source,
    title = title,
    section = section,
    chunkIndex = chunkIndex,
    charOffset = charOffset,
    tokenCount = tokenCount
)
