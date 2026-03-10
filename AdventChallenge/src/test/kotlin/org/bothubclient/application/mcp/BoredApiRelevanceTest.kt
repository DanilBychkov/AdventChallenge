package org.bothubclient.application.mcp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class BoredApiRelevanceTest {

    // English positive cases (boredom/activity intent only)
    @ParameterizedTest
    @ValueSource(
        strings = [
            "I'm bored, give me an activity",
            "Suggest some activities for the weekend",
            "I need ideas for what to do today",
            "Give me a random activity",
            "I'm feeling boredom, help me find something to do",
            "What to do when you're bored?",
            "I need something to do right now",
            "Looking for activity ideas",
            "I'm bored",
            "what to do",
            "activity ideas"
        ]
    )
    fun `isBoredApiRelevant returns true for activity and bored intents EN`(message: String) {
        assertTrue(isBoredApiRelevantMessage(message))
    }

    // Russian positive cases (boredom/activity/leisure intent only)
    @ParameterizedTest
    @ValueSource(
        strings = [
            "мне скучно",
            "чем заняться",
            "какое занятие предложить",
            "подскажи чем заняться",
            "чем развлечься сегодня",
            "предложи какое-нибудь занятие",
            "не знаю что делать",
            "нужны идеи для развлечений",
            "есть развлечения на вечер",
            "хочу чем-то заняться",
            "есть какие-нибудь занятия?",
            "идеи для досуга",
            "дай идеи чем заняться"
        ]
    )
    fun `isBoredApiRelevant returns true for activity and bored intents RU`(message: String) {
        assertTrue(isBoredApiRelevantMessage(message))
    }

    // English negative cases (unrelated or engineering "ideas/suggest" — must not trigger)
    @ParameterizedTest
    @ValueSource(
        strings = [
            "Write a haiku about spring",
            "Summarize this email thread",
            "Translate this sentence to Spanish",
            "What is the capital of France?",
            "Debug this code for me",
            "Calculate 15% of 200",
            "What's the weather like today?",
            "Tell me a joke about programming",
            "give me ideas for refactoring",
            "suggest architecture improvements"
        ]
    )
    fun `isBoredApiRelevant returns false for unrelated intents EN`(message: String) {
        assertFalse(isBoredApiRelevantMessage(message))
    }

    // Russian negative cases (unrelated or engineering "идеи/предложи" — must not trigger)
    @ParameterizedTest
    @ValueSource(
        strings = [
            "Напиши хайку о весне",
            "ПЕРЕВОД на английский",
            "Какая столица Франции?",
            "Помоги с кодом",
            "Расскажи анекдот",
            "Какая погода сегодня?",
            "Объясни теорию относительности",
            "Напиши письмо коллеге",
            "предложи архитектуру",
            "идеи по оптимизации кода"
        ]
    )
    fun `isBoredApiRelevant returns false for unrelated intents RU`(message: String) {
        assertFalse(isBoredApiRelevantMessage(message))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "I'm BORED, give me something",
            "ACTIVITY please",
            "What TO DO now",
            "МНЕ СКУЧНО",
            "ЧЕМ ЗАНЯТЬСЯ",
            "ИДЕИ для досуга",
            "ПРЕДЛОЖИ занятие"
        ]
    )
    fun `isBoredApiRelevant is case insensitive`(message: String) {
        assertTrue(isBoredApiRelevantMessage(message))
    }
}
