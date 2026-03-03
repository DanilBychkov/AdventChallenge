package org.bothubclient.domain.entity

enum class MemoryLayer {
    STM,
    WM,
    LTM
}

enum class WmCategory {
    USER_INFO,
    TASK,
    CONTEXT,
    PROGRESS
}

data class FactEntry(
    val value: String,
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "",
    var useCount: Int = 0,
    var lastUsed: Long = timestamp,
    val ttl: Long? = null
)

data class MemoryOperation(
    val op: String,
    val fromLayer: MemoryLayer? = null,
    val toLayer: MemoryLayer? = null,
    val category: WmCategory? = null,
    val key: String,
    val value: String? = null,
    val confidence: Float = 1.0f
)
