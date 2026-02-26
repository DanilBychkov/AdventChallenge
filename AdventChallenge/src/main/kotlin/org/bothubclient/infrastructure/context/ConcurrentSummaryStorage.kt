package org.bothubclient.infrastructure.context

import org.bothubclient.domain.context.SummaryStorage
import org.bothubclient.domain.entity.SummaryBlock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class ConcurrentSummaryStorage : SummaryStorage {

    private val storage = ConcurrentHashMap<String, CopyOnWriteArrayList<SummaryBlock>>()

    override fun getBlocks(sessionId: String): List<SummaryBlock> =
        storage[sessionId]?.toList().orEmpty()

    override fun addBlock(sessionId: String, block: SummaryBlock) {
        storage.computeIfAbsent(sessionId) { CopyOnWriteArrayList() }
            .add(block)
    }

    override fun removeBlock(sessionId: String, blockId: String): SummaryBlock? {
        val blocks = storage[sessionId] ?: return null
        val index = blocks.indexOfFirst { it.id == blockId }
        return if (index >= 0) blocks.removeAt(index) else null
    }

    override fun clear(sessionId: String) {
        storage.remove(sessionId)
    }

    override fun getAllSessions(): Set<String> = storage.keys.toSet()
}
