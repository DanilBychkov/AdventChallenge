package org.bothubclient.domain.entity

data class ComposedContext(
    val systemPrompt: String,
    val summaryBlocks: List<SummaryBlock>,
    val facts: Map<WmCategory, Map<String, FactEntry>>,
    val recentMessages: List<Message>,
    val userMessage: String,
    val includeAgentPrimer: Boolean,
    val taskContext: TaskContext? = null
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

    private fun taskStateText(): String {
        val ctx = taskContext
        if (ctx == null) return "state=IDLE"

        val planText =
            if (ctx.plan.isEmpty()) {
                "(no plan)"
            } else {
                ctx.plan.joinToString("\n") { step ->
                    val res =
                        step.result
                            ?.take(120)
                            ?.replace("\n", " ")
                            ?.let { " result=$it" }
                            .orEmpty()
                    "${step.id} [${step.status}] ${step.description.take(120)}$res"
                }
            }

        val current = ctx.plan.getOrNull(ctx.currentStepIndex)
        val currentText =
            current?.let { "${it.id} [${it.status}] ${it.description.take(120)}" }
                ?: "(no current step)"

        return buildString {
            appendLine("taskId=${ctx.taskId}")
            appendLine("state=${ctx.state}")
            appendLine("planApproved=${ctx.artifacts["planApproved"]}")
            appendLine("validationApproved=${ctx.artifacts["validationApproved"]}")
            appendLine("currentStepIndex=${ctx.currentStepIndex}")
            appendLine("currentStep=$currentText")
            if (ctx.error != null) appendLine("error=${ctx.error}")
            appendLine("plan:")
            append(planText)
        }
            .trim()
    }

    private fun taskProtocolText(): String {
        val ctx = taskContext ?: return ""
        return buildString {
            appendLine("Следуй строго состоянию state и не перескакивай шаги.")
            appendLine("Флоу: PLANNING → EXECUTION → VALIDATION → DONE.")
            appendLine("Правила:")
            appendLine(
                "- PLANNING: задай уточняющие вопросы, затем предложи план и попроси явное подтверждение. Пока planApproved=false — НЕ переходи к EXECUTION."
            )
            appendLine(
                "- EXECUTION: выполняй план по шагам. Фокусируйся на текущем шаге и в конце проси продолжить/подтвердить переход к следующему."
            )
            appendLine(
                "- VALIDATION: опиши проверки (тесты/ревью), покажи validationReport и попроси явное подтверждение завершения. Пока validationApproved=false — НЕ переходи к DONE."
            )
            appendLine("- DONE: зафиксируй итог (finalResult) кратко и по делу.")
            appendLine("Текущая задача: ${ctx.originalRequest.take(240)}")
        }
            .trim()
    }

    val factsText: String
        get() {
            if (facts.isEmpty()) return ""
            return buildString {
                append("[WORKING_MEMORY]\n")
                facts.toSortedMap().forEach { (category, group) ->
                    if (group.isEmpty()) return@forEach
                    append("[${category.name}]\n")
                    group.toSortedMap().forEach { (k, entry) ->
                        append(
                            "$k: ${entry.value} (confidence=${"%.2f".format(entry.confidence)}, useCount=${entry.useCount})\n"
                        )
                    }
                }
                append("[END WORKING_MEMORY]\n")
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
            if (taskContext != null) {
                append("\n\n")
                append("[TASK_PROTOCOL]\n")
                append(taskProtocolText())
                append("\n[END TASK_PROTOCOL]\n\n")
                append("[TASK_STATE]\n")
                append(taskStateText())
                append("\n[END TASK_STATE]\n")
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
