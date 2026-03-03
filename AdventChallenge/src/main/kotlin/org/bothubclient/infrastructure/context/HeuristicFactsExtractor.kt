package org.bothubclient.infrastructure.context

import org.bothubclient.domain.entity.FactEntry
import org.bothubclient.domain.entity.MemoryLayer
import org.bothubclient.domain.entity.MemoryOperation
import org.bothubclient.domain.entity.WmCategory
import org.bothubclient.infrastructure.logging.AppLogger
import java.util.*

class HeuristicFactsExtractor(private val llmFactsExtractor: LlmFactsExtractor? = null) {
    companion object {
        private const val TAG = "HeuristicFactsExtractor"
    }

    fun extractUpdates(userMessage: String): Map<String, String> {
        val msg = userMessage.trim()
        if (msg.isBlank()) return emptyMap()

        val lower = msg.lowercase(Locale.getDefault())
        val updates = linkedMapOf<String, String>()

        run {
            val marker = "меня зовут"
            val idx = lower.indexOf(marker)
            if (idx >= 0) {
                val raw = msg.substring((idx + marker.length).coerceAtMost(msg.length)).trim()
                val cleaned = raw.trim(' ', '.', ',', '!', '?', ':', ';', '"', '«', '»', '(', ')')
                val tokens =
                    cleaned.split(Regex("""\s+"""))
                        .asSequence()
                        .map { it.trim('-', '—', '–') }
                        .filter { it.isNotBlank() }
                        .map { it.trim(' ', '.', ',', '!', '?', ':', ';', '"', '«', '»') }
                        .filter { it.any { ch -> ch.isLetter() } }
                        .take(3)
                        .toList()
                if (tokens.isNotEmpty()) {
                    updates["user_name"] = tokens.joinToString(" ")
                }
            }
        }

        run {
            val marker = "my name is"
            val idx = lower.indexOf(marker)
            if (idx >= 0) {
                val raw = msg.substring((idx + marker.length).coerceAtMost(msg.length)).trim()
                val cleaned = raw.trim(' ', '.', ',', '!', '?', ':', ';', '"', '(', ')')
                val tokens =
                    cleaned.split(Regex("""\s+"""))
                        .asSequence()
                        .map { it.trim('-', '—', '–') }
                        .filter { it.isNotBlank() }
                        .map { it.trim(' ', '.', ',', '!', '?', ':', ';', '"') }
                        .filter { it.any { ch -> ch.isLetter() } }
                        .take(3)
                        .toList()
                if (tokens.isNotEmpty()) {
                    updates["user_name"] = tokens.joinToString(" ")
                }
            }
        }

        if (lower.contains("нужен") || lower.contains("нужна") || lower.contains("нужно")) {
            updates["goal"] = msg
        }

        if (lower.contains("команда") || lower.contains("человек")) {
            val team =
                Regex("""(\d{2,5})\s*(чел|человек)""", RegexOption.IGNORE_CASE)
                    .find(msg)
                    ?.groupValues
                    ?.getOrNull(1)
            if (team != null) updates["team_size"] = team

            val offices =
                Regex("""(\d{1,2})\s*офис""", RegexOption.IGNORE_CASE)
                    .find(msg)
                    ?.groupValues
                    ?.getOrNull(1)
            if (offices != null) updates["offices"] = offices
        }

        if (lower.contains("sso")) {
            updates["sso"] = "required"
        }

        if (lower.contains("google calendar") ||
            lower.contains("outlook") ||
            lower.contains("calendar")
        ) {
            val integrations =
                buildList {
                    if (lower.contains("google calendar")) add("Google Calendar")
                    if (lower.contains("outlook")) add("Outlook")
                }
                    .distinct()
            if (integrations.isNotEmpty()) updates["integrations"] = integrations.joinToString(", ")
        }

        if (lower.contains("доступ") || lower.contains("рол")) {
            updates["access_roles"] = msg
        }

        if (lower.contains("sla")) {
            val sla =
                Regex("""sla\s*([0-9]{2}\.?[0-9]?)\s*%""", RegexOption.IGNORE_CASE)
                    .find(msg)
                    ?.groupValues
                    ?.getOrNull(1)
            updates["sla"] = sla?.let { "$it%" } ?: msg
        }

        if (lower.contains("аудит")) {
            updates["audit"] = "required"
        }

        if (lower.contains("бюджет") || lower.contains("$/мес") || lower.contains("usd")) {
            updates["budget"] = msg
        }

        if (lower.contains("мобиль") || lower.contains("веб") || lower.contains("web")) {
            val platforms =
                buildList {
                    if (lower.contains("мобиль")) add("mobile")
                    if (lower.contains("веб") || lower.contains("web")) add("web")
                }
                    .distinct()
            if (platforms.isNotEmpty()) updates["platforms"] = platforms.joinToString(", ")
        }

        if (lower.contains("локализац") || lower.contains("en") || lower.contains("ru")) {
            val locales =
                buildList {
                    if (Regex("""\ben\b""", RegexOption.IGNORE_CASE)
                            .containsMatchIn(msg)
                    )
                        add("EN")
                    if (Regex("""\bru\b""", RegexOption.IGNORE_CASE)
                            .containsMatchIn(msg)
                    )
                        add("RU")
                }
                    .distinct()
            if (locales.isNotEmpty()) updates["locales"] = locales.joinToString(", ")
        }

        if (lower.contains("mvp") || lower.contains("срок")) {
            updates["mvp_timeline"] = msg
        }

        if (lower.contains("персональ") || lower.contains("30") && lower.contains("дн")) {
            updates["data_retention"] = msg
        }

        if (lower.contains("отч") || lower.contains("загрузк") || lower.contains("репорт")) {
            updates["reports"] = msg
        }

        return updates
    }

    fun extractUpdatedGroupsHeuristic(
        userMessage: String,
        existingGroups: Map<String, Map<String, String>>
    ): Map<String, Map<String, String>> {
        val updates = extractUpdates(userMessage)
        if (updates.isEmpty()) return existingGroups
        return mergeFlatUpdatesIntoGroups(updates, existingGroups)
    }

    suspend fun extractUpdatedGroups(
        userMessage: String,
        existingGroups: Map<String, Map<String, String>>
    ): Map<String, Map<String, String>> {
        val llm =
            llmFactsExtractor
                ?: return extractUpdatedGroupsHeuristic(userMessage, existingGroups)
        return try {
            val response =
                llm.extractFacts(userMessage = userMessage, existingGroups = existingGroups)
            if (response.facts.isEmpty()) {
                existingGroups
            } else {
                val updated =
                    mergeFactsIntoGroups(
                        facts = response.facts,
                        existingGroups = existingGroups
                    )
                if (response.mergeActions.isNotEmpty()) {
                    val updatesCount = response.mergeActions.count { it.action == "update_fact" }
                    val createsCount = response.mergeActions.count { it.action == "create_group" }
                    AppLogger.i(
                        TAG,
                        "LLM merge actions: create_group=$createsCount update_fact=$updatesCount"
                    )
                }
                updated
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "LLM facts extraction failed, using heuristics fallback", e)
            extractUpdatedGroupsHeuristic(userMessage, existingGroups)
        }
    }

    fun extractOperationsHeuristic(
        userMessage: String,
        existingWorkingMemory: Map<WmCategory, Map<String, FactEntry>>
    ): List<MemoryOperation> {
        val facts =
            extractUpdates(userMessage).map { (k, v) ->
                LlmFactsExtractor.Fact(
                    category = categoryForKey(k),
                    key = k,
                    value = v,
                    confidence = confidenceForKey(k)
                )
            }
        return buildExtractOps(facts, existingWorkingMemory)
    }

    suspend fun extractOperations(
        userMessage: String,
        existingWorkingMemory: Map<WmCategory, Map<String, FactEntry>>
    ): List<MemoryOperation> {
        val llm = llmFactsExtractor
        if (llm == null) return extractOperationsHeuristic(userMessage, existingWorkingMemory)

        val facts =
            runCatching {
                val existingLegacy =
                    existingWorkingMemory
                        .mapKeys { (cat, _) -> legacyCategoryForWm(cat) }
                        .mapValues { (_, group) ->
                            group.mapValues { (_, e) -> e.value }
                        }
                llm.extractFacts(userMessage = userMessage, existingGroups = existingLegacy)
                    .facts
            }
                .getOrElse { e ->
                    AppLogger.e(
                        TAG,
                        "LLM facts extraction failed, using heuristics fallback",
                        e
                    )
                    extractUpdates(userMessage).map { (k, v) ->
                        LlmFactsExtractor.Fact(
                            category = categoryForKey(k),
                            key = k,
                            value = v,
                            confidence = confidenceForKey(k)
                        )
                    }
                }

        return buildExtractOps(facts, existingWorkingMemory)
    }

    private fun buildExtractOps(
        facts: List<LlmFactsExtractor.Fact>,
        existingWorkingMemory: Map<WmCategory, Map<String, FactEntry>>
    ): List<MemoryOperation> {
        val ops = mutableListOf<MemoryOperation>()
        facts.forEach { fact ->
            val key = fact.key.trim()
            val value = fact.value.trim()
            if (key.isBlank() || value.isBlank()) return@forEach

            val category = wmCategoryForFact(fact)
            val existingValue = existingWorkingMemory[category]?.get(key)?.value
            if (existingValue == value) return@forEach

            val c = fact.confidence.coerceIn(0.0, 1.0).toFloat()
            ops +=
                MemoryOperation(
                    op = "EXTRACT",
                    fromLayer = MemoryLayer.STM,
                    toLayer = MemoryLayer.WM,
                    category = category,
                    key = key,
                    value = value,
                    confidence = c
                )
        }
        return ops
    }

    private fun wmCategoryForFact(fact: LlmFactsExtractor.Fact): WmCategory {
        return when (fact.category.trim().lowercase(Locale.getDefault())) {
            "identity", "preferences" -> WmCategory.USER_INFO
            "project", "requirements" -> WmCategory.TASK
            "constraints", "timeline", "progress" -> WmCategory.PROGRESS
            "technical", "business", "context", "other" -> WmCategory.CONTEXT
            else ->
                when (fact.key.trim().lowercase(Locale.getDefault())) {
                    "user_name",
                    "company",
                    "role",
                    "contact",
                    "language",
                    "timezone",
                    "locale",
                    "locales" -> WmCategory.USER_INFO

                    "name",
                    "description",
                    "scope",
                    "features",
                    "integrations",
                    "platforms",
                    "sso",
                    "compliance",
                    "access_roles",
                    "reports" -> WmCategory.TASK

                    "deadlines",
                    "milestones",
                    "budget",
                    "timeline",
                    "team_size",
                    "resources",
                    "offices" -> WmCategory.PROGRESS

                    else -> WmCategory.CONTEXT
                }
        }
    }

    private fun legacyCategoryForWm(category: WmCategory): String {
        return when (category) {
            WmCategory.USER_INFO -> "identity"
            WmCategory.TASK -> "project"
            WmCategory.CONTEXT -> "technical"
            WmCategory.PROGRESS -> "timeline"
        }
    }

    private fun confidenceForKey(key: String): Double {
        return when (key.trim().lowercase(Locale.getDefault())) {
            "user_name", "company", "role", "contact" -> 0.9
            "language", "timezone", "locale", "locales" -> 0.85
            "stack", "architecture", "api" -> 0.9
            "name", "description", "scope" -> 0.85
            "features",
            "integrations",
            "platforms",
            "sso",
            "compliance",
            "access_roles",
            "reports" -> 0.8

            "budget", "timeline", "team_size", "resources", "offices", "deadlines", "milestones" ->
                0.8

            else -> 0.7
        }
    }

    private fun mergeFlatUpdatesIntoGroups(
        updates: Map<String, String>,
        existingGroups: Map<String, Map<String, String>>
    ): Map<String, Map<String, String>> {
        val facts =
            updates.map { (k, v) ->
                LlmFactsExtractor.Fact(
                    category = categoryForKey(k),
                    key = k,
                    value = v,
                    confidence = 1.0
                )
            }
        return mergeFactsIntoGroups(facts, existingGroups)
    }

    private fun mergeFactsIntoGroups(
        facts: List<LlmFactsExtractor.Fact>,
        existingGroups: Map<String, Map<String, String>>
    ): Map<String, Map<String, String>> {
        val result = LinkedHashMap<String, LinkedHashMap<String, String>>()
        existingGroups.forEach { (category, group) ->
            val key = category.trim().ifBlank { "other" }
            val existing = result.getOrPut(key) { LinkedHashMap() }
            group.forEach { (k, v) ->
                val kk = k.trim()
                val vv = v.trim()
                if (kk.isNotBlank() && vv.isNotBlank()) existing[kk] = vv
            }
        }

        facts.forEach { fact ->
            val category = fact.category.trim().ifBlank { "other" }
            val key = fact.key.trim()
            val value = fact.value.trim()
            if (key.isBlank() || value.isBlank()) return@forEach

            val group = result.getOrPut(category) { LinkedHashMap() }
            val previous = group[key]
            if (previous == null) {
                group[key] = value
            } else if (previous != value) {
                group[key] = value
            }
        }

        val out: LinkedHashMap<String, Map<String, String>> = LinkedHashMap()
        result.forEach { (category, group) -> if (group.isNotEmpty()) out[category] = group }
        return out
    }

    private fun categoryForKey(key: String): String {
        return when (key) {
            "user_name", "company", "role", "contact" -> "identity"
            "name", "description", "scope" -> "project"
            "features",
            "integrations",
            "platforms",
            "sso",
            "compliance",
            "access_roles",
            "reports" -> "requirements"
            "budget", "timeline", "team_size", "resources", "offices" -> "constraints"
            "language", "timezone", "locale", "locales" -> "preferences"
            "stack", "architecture", "api" -> "technical"
            "sla", "audit", "security", "mvp_timeline" -> "business"
            "deadlines", "milestones" -> "timeline"
            else -> "other"
        }
    }
}
