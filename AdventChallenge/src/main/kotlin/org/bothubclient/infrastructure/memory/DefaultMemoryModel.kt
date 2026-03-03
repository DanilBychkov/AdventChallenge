package org.bothubclient.infrastructure.memory

import org.bothubclient.domain.entity.*
import org.bothubclient.domain.memory.LongTermMemoryStore
import org.bothubclient.domain.memory.MemoryItem
import org.bothubclient.domain.memory.MemoryModel
import org.bothubclient.infrastructure.context.HeuristicFactsExtractor

class DefaultMemoryModel(
    private val shortTermMemory: MutableList<Message>,
    private val workingMemory: LinkedHashMap<WmCategory, LinkedHashMap<String, FactEntry>>,
    private val longTermMemoryStore: LongTermMemoryStore,
    private val factsExtractor: HeuristicFactsExtractor,
    private val maxFacts: Int
) : MemoryModel {

    override fun observe(message: Message) {
        shortTermMemory.add(message)
    }

    override suspend fun extract(message: String): List<MemoryOperation> {
        val snapshot = snapshotWorkingMemory()
        val ops = factsExtractor.extractOperations(message, snapshot)
        if (ops.isEmpty()) return emptyList()
        applyExtractOperations(ops)
        val forgetOps = trimWorkingMemory()
        return ops + forgetOps
    }

    override suspend fun persist(item: MemoryItem): Boolean = longTermMemoryStore.upsert(item)

    override suspend fun recall(query: String, limit: Int): List<MemoryItem> =
        longTermMemoryStore.search(query, limit)

    override suspend fun forget(criteria: (MemoryItem) -> Boolean) {
        val toRemove = mutableListOf<Pair<WmCategory, String>>()
        workingMemory.forEach { (cat, group) ->
            group.forEach { (key, entry) ->
                if (criteria(MemoryItem(cat, key, entry))) {
                    toRemove += cat to key
                }
            }
        }
        toRemove.forEach { (cat, key) ->
            val group = workingMemory[cat] ?: return@forEach
            group.remove(key)
            if (group.isEmpty()) workingMemory.remove(cat)
        }
        longTermMemoryStore.deleteWhere(criteria)
    }

    private fun snapshotWorkingMemory(): Map<WmCategory, Map<String, FactEntry>> {
        val out = LinkedHashMap<WmCategory, Map<String, FactEntry>>()
        workingMemory.forEach { (category, group) ->
            out[category] = group.mapValues { (_, entry) -> entry.copy() }
        }
        return out
    }

    private fun applyExtractOperations(ops: List<MemoryOperation>) {
        val now = System.currentTimeMillis()
        ops.forEach { op ->
            if (op.op != "EXTRACT") return@forEach
            val category = op.category ?: return@forEach
            val key = op.key.trim()
            val value = op.value?.trim().orEmpty()
            if (key.isBlank() || value.isBlank()) return@forEach

            val group = workingMemory.getOrPut(category) { LinkedHashMap() }
            val prev = group[key]
            group[key] =
                if (prev == null) {
                    FactEntry(
                        value = value,
                        confidence = op.confidence,
                        timestamp = now,
                        source = "extract",
                        useCount = 0,
                        lastUsed = now
                    )
                } else {
                    prev.copy(
                        value = value,
                        confidence = op.confidence,
                        timestamp = now,
                        source = "extract",
                        lastUsed = now,
                        useCount = prev.useCount
                    )
                }
        }
        workingMemory.entries.removeIf { it.value.isEmpty() }
    }

    private fun totalFacts(): Int = workingMemory.values.sumOf { it.size }

    private fun trimWorkingMemory(): List<MemoryOperation> {
        val removed = mutableListOf<MemoryOperation>()
        while (totalFacts() > maxFacts) {
            var victimCategory: WmCategory? = null
            var victimKey: String? = null
            var victimScore = Double.POSITIVE_INFINITY
            var victimTimestamp = Long.MAX_VALUE
            var victimEntry: FactEntry? = null

            workingMemory.forEach { (category, group) ->
                group.forEach { (key, entry) ->
                    val score = (entry.confidence.toDouble()) * (entry.useCount.toDouble() + 1.0)
                    if (score < victimScore || (score == victimScore && entry.timestamp < victimTimestamp)) {
                        victimScore = score
                        victimTimestamp = entry.timestamp
                        victimCategory = category
                        victimKey = key
                        victimEntry = entry
                    }
                }
            }

            val cat = victimCategory ?: break
            val key = victimKey ?: break
            val group = workingMemory[cat] ?: break
            group.remove(key)
            victimEntry?.let { entry ->
                removed +=
                    MemoryOperation(
                        op = "FORGET",
                        fromLayer = MemoryLayer.WM,
                        toLayer = null,
                        category = cat,
                        key = key,
                        value = entry.value,
                        confidence = entry.confidence
                    )
            }
            if (group.isEmpty()) workingMemory.remove(cat)
        }
        return removed
    }
}
