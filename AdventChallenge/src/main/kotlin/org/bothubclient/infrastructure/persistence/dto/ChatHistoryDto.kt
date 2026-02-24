@file:UseSerializers(InstantAsIso8601Serializer::class)

package org.bothubclient.infrastructure.persistence.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.MessageMetrics
import org.bothubclient.domain.entity.MessageRole
import java.time.Instant
import java.time.format.DateTimeFormatter

@Serializable
data class ChatHistoryDto(
    val version: Int = 1,
    val sessionId: String,
    val messages: List<MessageDto>,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class MessageDto(
    val role: String,
    val content: String,
    val timestamp: String = "",
    val metrics: MessageMetricsDto? = null
)

@Serializable
data class MessageMetricsDto(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val responseTimeMs: Long = 0,
    val cost: Double? = null
)

fun MessageDto.toDomain(): Message = Message(
    role = MessageRole.valueOf(role),
    content = content,
    timestamp = timestamp,
    metrics = metrics?.toDomain()
)

fun Message.toDto(): MessageDto = MessageDto(
    role = role.name,
    content = content,
    timestamp = timestamp,
    metrics = metrics?.toDto()
)

fun MessageMetricsDto.toDomain(): MessageMetrics = MessageMetrics(
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    totalTokens = totalTokens,
    responseTimeMs = responseTimeMs,
    cost = cost
)

fun MessageMetrics.toDto(): MessageMetricsDto = MessageMetricsDto(
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    totalTokens = totalTokens,
    responseTimeMs = responseTimeMs,
    cost = cost
)

object InstantAsIso8601Serializer : kotlinx.serialization.KSerializer<Instant> {
    private val formatter = DateTimeFormatter.ISO_INSTANT
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "Instant",
        kotlinx.serialization.descriptors.PrimitiveKind.STRING
    )

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Instant) {
        encoder.encodeString(formatter.format(value))
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}
