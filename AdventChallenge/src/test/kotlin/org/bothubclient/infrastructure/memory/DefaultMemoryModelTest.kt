package org.bothubclient.infrastructure.memory

import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.entity.FactEntry
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.WmCategory
import org.bothubclient.domain.memory.LongTermMemoryStore
import org.bothubclient.domain.memory.MemoryItem
import org.bothubclient.infrastructure.context.HeuristicFactsExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultMemoryModelTest {

    private class FakeLongTermMemoryStore : LongTermMemoryStore {
        private val items = LinkedHashMap<String, MemoryItem>()

        private fun id(item: MemoryItem): String = "${item.category.name}::${item.key}"

        override suspend fun upsert(item: MemoryItem): Boolean {
            items[id(item)] = item
            return true
        }

        override suspend fun search(query: String, limit: Int): List<MemoryItem> {
            val q = query.trim().lowercase()
            if (q.isBlank()) return emptyList()
            return items.values
                .filter { (it.key + " " + it.entry.value).lowercase().contains(q) }
                .take(limit)
        }

        override suspend fun snapshot(): List<MemoryItem> = items.values.toList()

        override suspend fun deleteWhere(criteria: (MemoryItem) -> Boolean): Int {
            val toRemove = items.values.filter(criteria).map { id(it) }
            toRemove.forEach { items.remove(it) }
            return toRemove.size
        }
    }

    @Test
    fun extract_should_write_to_working_memory_and_return_extract_op() = runTest {
        val stm = mutableListOf<Message>()
        val wm = LinkedHashMap<WmCategory, LinkedHashMap<String, FactEntry>>()
        val store = FakeLongTermMemoryStore()
        val extractor = HeuristicFactsExtractor()
        val model = DefaultMemoryModel(stm, wm, store, extractor, maxFacts = 24)

        val ops = model.extract("Меня зовут Иван")

        assertTrue(ops.any { it.op == "EXTRACT" && it.category == WmCategory.USER_INFO && it.key == "user_name" })
        val saved = wm[WmCategory.USER_INFO]?.get("user_name")
        assertNotNull(saved)
        assertEquals("Иван", saved.value)
    }

    @Test
    fun extract_should_trim_working_memory_and_return_forget_ops() = runTest {
        val stm = mutableListOf<Message>()
        val wm = LinkedHashMap<WmCategory, LinkedHashMap<String, FactEntry>>()
        val store = FakeLongTermMemoryStore()
        val extractor = HeuristicFactsExtractor()
        val model = DefaultMemoryModel(stm, wm, store, extractor, maxFacts = 1)

        model.extract("Меня зовут Иван")
        val ops = model.extract("Команда 200 человек, 4 офиса")

        assertTrue(ops.any { it.op == "FORGET" })
        val total = wm.values.sumOf { it.size }
        assertEquals(1, total)
    }

    @Test
    fun persist_recall_and_forget_should_delegate_to_long_term_store() = runTest {
        val stm = mutableListOf<Message>()
        val wm = LinkedHashMap<WmCategory, LinkedHashMap<String, FactEntry>>()
        val store = FakeLongTermMemoryStore()
        val extractor = HeuristicFactsExtractor()
        val model = DefaultMemoryModel(stm, wm, store, extractor, maxFacts = 24)

        val item =
            MemoryItem(
                category = WmCategory.CONTEXT,
                key = "stack",
                entry = FactEntry(value = "Kotlin, Compose Desktop", confidence = 0.95f, source = "test")
            )

        val ok = model.persist(item)
        assertTrue(ok)

        val recalled = model.recall("compose", limit = 10)
        assertEquals(1, recalled.size)
        assertEquals("stack", recalled.first().key)

        model.forget { it.key == "stack" }
        val after = model.recall("compose", limit = 10)
        assertTrue(after.isEmpty())
    }
}
