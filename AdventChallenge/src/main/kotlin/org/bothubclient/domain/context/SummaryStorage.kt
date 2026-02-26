package org.bothubclient.domain.context

import org.bothubclient.domain.entity.SummaryBlock

interface SummaryStorage {
    fun getBlocks(sessionId: String): List<SummaryBlock>
    fun addBlock(sessionId: String, block: SummaryBlock)
    fun removeBlock(sessionId: String, blockId: String): SummaryBlock?
    fun clear(sessionId: String)
    fun getAllSessions(): Set<String>
}
