package org.bothubclient.domain.entity

data class ComposedContext(
    val systemPrompt: String,
    val summaryBlocks: List<SummaryBlock>,
    val facts: Map<String, Map<String, String>>,
    val recentMessages: List<Message>,
    val userMessage: String,
    val includeAgentPrimer: Boolean
) {
    val totalEstimatedTokens: Int by lazy {
        val systemTokens = systemPrompt.length / 4
        val summaryTokens = summaryBlocks.sumOf { it.estimatedTokens }
        val factsTokens = factsText.length / 4
        val recentTokens = recentMessages.sumOf { it.content.length / 4 }
        val userTokens = userMessage.length / 4
        systemTokens + factsTokens + summaryTokens + recentTokens + userTokens
    }

    private val agentPrimerText: String by lazy {
        if (!includeAgentPrimer) return@lazy ""
        """
        [АГЕНТНЫЙ РЕЖИМ]
        Определения:
        - AI agent — система, которая самостоятельно выбирает действия для достижения цели, опираясь на состояние (память/контекст) и обратную связь.
        - Agentic workflow — заранее спроектированный процесс (цепочка шагов/ролей/инструментов), где “агентность” ограничена рамками сценария.
        
        Поведенческий цикл: Research → Reason → Execute → Adapt → Remember.
        Итерации: линейные (один проход) и нелинейные (возврат к Research/Reason при новых данных или ошибках).
        [КОНЕЦ АГЕНТНОГО РЕЖИМА]
        """.trimIndent()
    }

    val factsText: String
        get() {
            if (facts.isEmpty()) return ""
            return buildString {
                append("[FACTS]\n")
                facts.toSortedMap().forEach { (category, group) ->
                    if (group.isEmpty()) return@forEach
                    append("[$category]\n")
                    group.toSortedMap().forEach { (k, v) -> append("$k: $v\n") }
                }
                append("[END FACTS]\n")
            }
        }

    val summaryContextText: String
        get() {
            if (summaryBlocks.isEmpty()) return ""
            return buildString {
                append("[ПРЕДЫДУЩИЙ КОНТЕКСТ РАЗГОВОРА]\n")
                summaryBlocks.forEachIndexed { index, block ->
                    append("[Блок ${index + 1}]: ${block.summary}\n")
                }
                append("[КОНЕЦ КОНТЕКСТА]\n")
            }
        }

    fun buildSystemPromptWithContext(): String {
        return buildString {
            append(systemPrompt)
            if (agentPrimerText.isNotBlank()) {
                append("\n\n")
                append(agentPrimerText)
            }
            if (factsText.isNotBlank()) {
                append("\n\n")
                append(factsText)
            }
            if (summaryContextText.isNotBlank()) {
                append("\n\n")
                append(summaryContextText)
            }
        }
    }
}
