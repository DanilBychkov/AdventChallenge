package org.bothubclient.domain.entity

data class ComposedContext(
    val systemPrompt: String,
    val summaryBlocks: List<SummaryBlock>,
    val recentMessages: List<Message>,
    val userMessage: String
) {
    val totalEstimatedTokens: Int by lazy {
        val systemTokens = systemPrompt.length / 4
        val summaryTokens = summaryBlocks.sumOf { it.estimatedTokens }
        val recentTokens = recentMessages.sumOf { it.content.length / 4 }
        val userTokens = userMessage.length / 4
        systemTokens + summaryTokens + recentTokens + userTokens
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
        if (summaryBlocks.isEmpty()) return systemPrompt
        return buildString {
            append(systemPrompt)
            append("\n\n")
            append(summaryContextText)
        }
    }
}
