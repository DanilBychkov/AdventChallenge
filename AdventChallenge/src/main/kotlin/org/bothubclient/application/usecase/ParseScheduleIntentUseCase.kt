package org.bothubclient.application.usecase

data class ScheduleIntent(
    val intent: String,
    val intervalMinutes: Int?,
    val enabled: Boolean?,
    val confidence: Double
) {
    companion object {
        const val SCHEDULE_BORED_REPORT = "SCHEDULE_BORED_REPORT"
        const val NONE = "NONE"
    }
}

class ParseScheduleIntentUseCase(
    private val llmExtractor: (suspend (String) -> ScheduleIntent)? = null
) {
    private val regexPatterns = listOf(
        Regex("""(?:раз в|каждые?)\s+(\d+)\s*(?:мин|минут)""", RegexOption.IGNORE_CASE),
        Regex("""(?:через каждые?|каждые?)\s+(\d+)\s*(?:мин|минут)""", RegexOption.IGNORE_CASE),
        Regex(
            """(\d+)\s*(?:мин|минут).*(?:присылай|отправляй|пиши|подсказывай|напоминай|показывай|рекомендуй)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """(?:присылай|отправляй|пиши|подсказывай|напоминай|показывай|рекомендуй).*(?:раз в|каждые?)\s+(\d+)\s*(?:мин|минут)""",
            RegexOption.IGNORE_CASE
        ),
    )

    private val hourPattern =
        Regex("""(?:каждый|раз в|каждые?|каждую)\s+(?:час|1\s*час)""", RegexOption.IGNORE_CASE)

    private val everyMinutePattern =
        Regex("""(?:каждую|каждые?)\s+(?:минуту|минутку)""", RegexOption.IGNORE_CASE)

    private val halfHourPattern =
        Regex("""(?:каждые?|раз в)\s+полчаса""", RegexOption.IGNORE_CASE)

    private val scheduleKeywords = listOf(
        "присылай", "отправляй", "подсказывай", "подсказывая", "напоминай", "напоминая",
        "показывай", "показывая", "присылай", "рекомендуй",
        "пиши", "чем заняться", "что делать", "что мне делать", "активност",
        "фоновую задачу", "фоновый", "периодически",
        "раз в", "каждые", "каждый", "каждую", "интервал",
        "запусти задачу", "настрой задачу"
    )

    suspend operator fun invoke(userMessage: String): ScheduleIntent {
        val ruleResult = tryRuleBased(userMessage)
        if (ruleResult != null) return ruleResult

        if (llmExtractor != null) {
            return runCatching { llmExtractor!!.invoke(userMessage) }
                .getOrDefault(ScheduleIntent(ScheduleIntent.NONE, null, null, 0.0))
        }

        return ScheduleIntent(ScheduleIntent.NONE, null, null, 0.0)
    }

    private fun tryRuleBased(message: String): ScheduleIntent? {
        val lower = message.lowercase()
        val hasKeyword = scheduleKeywords.any { lower.contains(it) }
        if (!hasKeyword) return null

        if (everyMinutePattern.containsMatchIn(lower)) {
            return ScheduleIntent(
                intent = ScheduleIntent.SCHEDULE_BORED_REPORT,
                intervalMinutes = 1,
                enabled = true,
                confidence = 0.95
            )
        }

        if (halfHourPattern.containsMatchIn(lower)) {
            return ScheduleIntent(
                intent = ScheduleIntent.SCHEDULE_BORED_REPORT,
                intervalMinutes = 30,
                enabled = true,
                confidence = 0.95
            )
        }

        if (hourPattern.containsMatchIn(lower)) {
            return ScheduleIntent(
                intent = ScheduleIntent.SCHEDULE_BORED_REPORT,
                intervalMinutes = 60,
                enabled = true,
                confidence = 0.95
            )
        }

        for (pattern in regexPatterns) {
            val match = pattern.find(lower)
            if (match != null) {
                val minutes = match.groupValues[1].toIntOrNull()
                if (minutes != null && minutes in 1..1440) {
                    return ScheduleIntent(
                        intent = ScheduleIntent.SCHEDULE_BORED_REPORT,
                        intervalMinutes = minutes,
                        enabled = true,
                        confidence = 0.9
                    )
                }
            }
        }

        if (hasKeyword) {
            val minuteMatch = Regex("""(\d+)\s*мин""", RegexOption.IGNORE_CASE).find(lower)
            if (minuteMatch != null) {
                val min = minuteMatch.groupValues[1].toIntOrNull()
                if (min != null && min in 1..1440) {
                    return ScheduleIntent(
                        intent = ScheduleIntent.SCHEDULE_BORED_REPORT,
                        intervalMinutes = min,
                        enabled = true,
                        confidence = 0.8
                    )
                }
            }
        }

        return null
    }
}
