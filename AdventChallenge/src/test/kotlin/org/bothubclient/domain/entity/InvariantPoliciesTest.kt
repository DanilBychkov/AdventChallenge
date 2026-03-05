package org.bothubclient.domain.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvariantPoliciesTest {
    @Test
    fun `sanitize removes name after russian greeting even without forbidden list`() {
        val (out, changed) = sanitizeAssistantTextNoUserName("Привет, Данил!", emptySet())
        assertEquals("Привет!", out)
        assertTrue(changed)
    }

    @Test
    fun `sanitize removes name after russian greeting with exclamation`() {
        val (out, changed) = sanitizeAssistantTextNoUserName("Привет! Данил", emptySet())
        assertEquals("Привет!", out)
        assertTrue(changed)
    }

    @Test
    fun `sanitize removes name after russian greeting with dot`() {
        val (out, changed) = sanitizeAssistantTextNoUserName("Привет. Данил", emptySet())
        assertEquals("Привет.", out)
        assertTrue(changed)
    }

    @Test
    fun `sanitize removes vocative name in middle with commas`() {
        val (out, changed) = sanitizeAssistantTextNoUserName("Так, Данил, давай продолжим.", emptySet())
        assertEquals("Так, давай продолжим.", out)
        assertTrue(changed)
    }

    @Test
    fun `sanitize removes leading dash name`() {
        val (out, changed) = sanitizeAssistantTextNoUserName("— Данил, привет!", emptySet())
        assertEquals("— привет!", out)
        assertTrue(changed)
    }

    @Test
    fun `sanitize removes name in parentheses after greeting`() {
        val (out, changed) = sanitizeAssistantTextNoUserName("Привет! (Данил)", emptySet())
        assertEquals("Привет!", out)
        assertTrue(changed)
    }

    @Test
    fun `sanitize removes forbidden name anywhere`() {
        val (out, changed) = sanitizeAssistantTextNoUserName("Спасибо, Данил. Держи ответ.", setOf("Данил"))
        assertEquals("Спасибо. Держи ответ.", out)
        assertTrue(changed)
    }

    @Test
    fun `sanitize removes name after english greeting`() {
        val (out, changed) = sanitizeAssistantTextNoUserName("Hello, Danil!", emptySet())
        assertEquals("Hello!", out)
        assertTrue(changed)
    }

    @Test
    fun `sanitize removes name after english greeting with exclamation`() {
        val (out, changed) = sanitizeAssistantTextNoUserName("Hello! Danil", emptySet())
        assertEquals("Hello!", out)
        assertTrue(changed)
    }

    @Test
    fun `sanitize removes name-first greeting`() {
        val (out, changed) = sanitizeAssistantTextNoUserName("Данил, привет! Как дела?", emptySet())
        assertEquals("привет! Как дела?", out)
        assertTrue(changed)
    }

    @Test
    fun `sanitize removes vocative name without knowing it`() {
        val (out, changed) = sanitizeAssistantTextNoUserName("Спасибо, Данил.", emptySet())
        assertEquals("Спасибо.", out)
        assertTrue(changed)
    }
}
