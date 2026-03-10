package org.bothubclient.application.mcp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class BoredApiRelevanceTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "I'm bored, give me an activity",
            "Suggest some activities for the weekend",
            "I need ideas for what to do today",
            "Give me a random activity",
            "I'm feeling boredom, help me find something to do",
            "Any suggestions for fun things to do?",
            "What to do when you're bored?",
            "I need something to do right now",
            "Can you give me an idea?",
            "Looking for activity ideas"
        ]
    )
    fun `isBoredApiRelevant returns true for activity and bored intents`(message: String) {
        assertTrue(isBoredApiRelevantMessage(message))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Write a haiku about spring",
            "Summarize this email thread",
            "Translate this sentence to Spanish",
            "What is the capital of France?",
            "Debug this code for me",
            "Calculate 15% of 200"
        ]
    )
    fun `isBoredApiRelevant returns false for unrelated intents`(message: String) {
        assertFalse(isBoredApiRelevantMessage(message))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "I'm BORED, give me something",
            "ACTIVITY please",
            "Give me IDEAS",
            "What TO DO now",
            "SUGGESTIONS needed"
        ]
    )
    fun `isBoredApiRelevant is case insensitive`(message: String) {
        assertTrue(isBoredApiRelevantMessage(message))
    }
}
