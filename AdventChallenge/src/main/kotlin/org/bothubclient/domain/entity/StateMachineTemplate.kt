package org.bothubclient.domain.entity

/**
 * Готовые шаблоны поведения LLM State Machine.
 * Определяют, какие шаги выполняются при переключении между состояниями.
 */
enum class StateMachineTemplate(val displayName: String, val description: String) {
    /** Обязательный шаблон: уточнение (Question) + реализация (Realization). Без валидации. */
    QUESTION_AND_REALIZATION(
        displayName = "Уточнение + Реализация",
        description = "Планирование и выполнение шагов без этапа валидации"
    ),

    /** Полный цикл: планирование, выполнение, валидация с утверждением пользователя. */
    FULL_PIPELINE(
        displayName = "Полный цикл",
        description = "Планирование → Реализация → Валидация с утверждением"
    ),

    /** Только реализация: без этапа уточнения/планирования (прямое выполнение). */
    REALIZATION_ONLY(
        displayName = "Только реализация",
        description = "Выполнение без отдельного этапа планирования"
    ),

    /** Только уточнение: планирование без автоматической реализации. */
    PLANNING_ONLY(
        displayName = "Только уточнение",
        description = "Планирование и уточнение без перехода к выполнению"
    )
}
