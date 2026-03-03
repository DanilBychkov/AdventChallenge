package org.bothubclient.domain.memory

import org.bothubclient.domain.entity.FactEntry
import org.bothubclient.domain.entity.WmCategory

data class MemoryItem(
    val category: WmCategory,
    val key: String,
    val entry: FactEntry
)

