package org.bothubclient.domain.usecase

import org.bothubclient.domain.entity.*

class UserProfilePromptBuilder {
    fun build(userProfile: UserProfile): String {
        val forbidUserName = userProfile.invariants.forbidsAddressingUserByName()
        return buildString {
            appendLine("## USER PROFILE")
            appendInvariants(userProfile.invariants)
            appendIdentity(userProfile.identity, forbidUserName)
            appendPreferences(userProfile.preferences)
            appendRules(userProfile.behaviorRules)
            appendContext(userProfile.context)
        }.trim()
    }

    private fun StringBuilder.appendIdentity(identity: UserProfileIdentity, forbidUserName: Boolean) {
        if (identity.displayName.isBlank() && identity.role.isBlank() && identity.expertiseAreas.isEmpty()) {
            return
        }
        if (!forbidUserName && identity.displayName.isNotBlank()) {
            appendLine("- Обращайся к пользователю: ${identity.displayName}")
        }
        if (identity.role.isNotBlank()) {
            appendLine("- Роль пользователя: ${identity.role}")
        }
        if (identity.expertiseAreas.isNotEmpty()) {
            appendLine("- Области экспертизы: ${identity.expertiseAreas.joinToString()}")
        }
    }

    private fun StringBuilder.appendPreferences(prefs: UserPreferences) {
        appendLine("- Уровень детализации: ${prefs.technicalLevel.asRu()}")
        appendLine("- Формальность: ${prefs.communicationStyle.formality.asRu()}")
        appendLine("- Подробность: ${prefs.communicationStyle.verbosity.asRu()}")
        appendLine("- Тон: ${prefs.communicationStyle.tone.asRu()}")
        appendLine("- Язык ответов: ${prefs.language.primaryLanguage}")
        appendLine("- Формат: ${if (prefs.responseFormat.preferLists) "списки" else "текст"}")
        appendLine("- Markdown: ${if (prefs.responseFormat.useMarkdown) "да" else "нет"}")
        appendLine("- Код-блоки: ${prefs.responseFormat.codeBlockStyle.asRu()}")
        appendLine("- Итоги: ${if (prefs.responseFormat.includeSummaries) "да" else "нет"}")
    }

    private fun StringBuilder.appendRules(rules: List<BehaviorRule>) {
        if (rules.isEmpty()) return
        appendLine()
        appendLine("## ОБЯЗАТЕЛЬНЫЕ ПРАВИЛА")
        rules.sortedByDescending { it.priority }.forEach { rule ->
            val condition = rule.condition.trim().ifBlank { "всегда" }
            val action = rule.action.trim()
            if (action.isNotBlank()) {
                appendLine("- ЕСЛИ $condition ТО $action")
            }
        }
    }

    private fun StringBuilder.appendContext(context: UserContext) {
        val hasAny =
            context.projectContext.isNotBlank() ||
                    context.companyContext.isNotBlank() ||
                    context.currentGoals.isNotEmpty() ||
                    context.avoidedTopics.isNotEmpty() ||
                    context.preferredTechnologies.isNotEmpty()
        if (!hasAny) return

        appendLine()
        appendLine("## КОНТЕКСТ")
        if (context.projectContext.isNotBlank()) {
            appendLine("Проект: ${context.projectContext}")
        }
        if (context.companyContext.isNotBlank()) {
            appendLine("Компания: ${context.companyContext}")
        }
        if (context.currentGoals.isNotEmpty()) {
            appendLine("Цели: ${context.currentGoals.joinToString()}")
        }
        if (context.avoidedTopics.isNotEmpty()) {
            appendLine("Избегать тем: ${context.avoidedTopics.joinToString()}")
        }
        if (context.preferredTechnologies.isNotEmpty()) {
            appendLine("Предпочтительные технологии:")
            context.preferredTechnologies.toSortedMap().forEach { (category, techs) ->
                if (category.isNotBlank() && techs.isNotEmpty()) {
                    appendLine("  $category: ${techs.joinToString()}")
                }
            }
        }
    }

    private fun StringBuilder.appendInvariants(invariants: List<ProfileInvariant>) {
        val active = invariants.filter { it.isActive }
        if (active.isEmpty()) return

        appendLine()
        appendLine("## ИНВАРИАНТЫ (НЕНАРУШИМЫЕ ПРАВИЛА)")
        appendLine("Ты ОБЯЗАН соблюдать эти правила. Нарушение запрещено.")
        appendLine("Перед тем как написать любое предложение, проверь его на конфликт с инвариантами.")
        appendLine("Если предложение нарушает HARD-инвариант — откажись и предложи альтернативу в рамках инвариантов.")
        appendLine("Если нарушает SOFT-инвариант — предупреди и предложи вариант в рамках инвариантов.")
        appendLine("Если ADVISORY — упомяни рекомендацию, но продолжай в рамках остальных правил.")
        appendLine()
        appendLine("Формат при конфликте:")
        appendLine("⚠️ КОНФЛИКТ С ИНВАРИАНТОМ")
        appendLine("Запрос: [что просил пользователь]")
        appendLine("Инвариант: [какой инвариант нарушается]")
        appendLine("Решение: [альтернатива в рамках инвариантов]")
        appendLine()

        active
            .groupBy { it.category }
            .toSortedMap(compareBy { it.ordinal })
            .forEach { (cat, items) ->
                appendLine("### ${cat.asRu()}")
                items.forEach { inv ->
                    val mark =
                        when (inv.severity) {
                            InvariantSeverity.HARD -> "⛔"
                            InvariantSeverity.SOFT -> "⚠️"
                            InvariantSeverity.ADVISORY -> "ℹ️"
                        }
                    appendLine("$mark ${inv.description}")
                    if (inv.rationale.isNotBlank()) {
                        appendLine("   Обоснование: ${inv.rationale}")
                    }
                }
            }
    }
}

private fun TechnicalLevel.asRu(): String =
    when (this) {
        TechnicalLevel.BEGINNER -> "новичок"
        TechnicalLevel.INTERMEDIATE -> "продвинутый"
        TechnicalLevel.ADVANCED -> "экспертный"
        TechnicalLevel.EXPERT -> "эксперт"
    }

private fun FormalityLevel.asRu(): String =
    when (this) {
        FormalityLevel.CASUAL -> "неформально"
        FormalityLevel.NEUTRAL -> "нейтрально"
        FormalityLevel.FORMAL -> "формально"
    }

private fun VerbosityLevel.asRu(): String =
    when (this) {
        VerbosityLevel.CONCISE -> "кратко"
        VerbosityLevel.MODERATE -> "умеренно"
        VerbosityLevel.DETAILED -> "подробно"
    }

private fun ToneStyle.asRu(): String =
    when (this) {
        ToneStyle.NEUTRAL -> "нейтральный"
        ToneStyle.FRIENDLY -> "дружелюбный"
        ToneStyle.FRIENDLY_PROFESSIONAL -> "дружелюбно-профессиональный"
        ToneStyle.STRICT_PROFESSIONAL -> "строго-профессиональный"
    }

private fun CodeBlockStyle.asRu(): String =
    when (this) {
        CodeBlockStyle.MINIMAL -> "минимально"
        CodeBlockStyle.WITH_COMMENTS -> "с комментариями"
        CodeBlockStyle.WITH_EXPLANATION -> "с объяснением"
        CodeBlockStyle.FULL_DOCUMENTATION -> "полная документация"
    }

private fun InvariantCategory.asRu(): String =
    when (this) {
        InvariantCategory.COMMON -> "Общие (всегда)"
        InvariantCategory.ARCHITECTURE -> "Архитектура"
        InvariantCategory.TECH_STACK -> "Техстек"
        InvariantCategory.BUSINESS_RULES -> "Бизнес-правила"
        InvariantCategory.SECURITY -> "Безопасность"
        InvariantCategory.CODE_STYLE -> "Стиль кода"
        InvariantCategory.CONSTRAINTS -> "Ограничения"
    }
