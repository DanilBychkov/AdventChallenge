package org.bothubclient.domain.usecase

import org.bothubclient.domain.entity.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserProfilePromptBuilderTest {
    @Test
    fun `build includes active invariants`() {
        val profile =
            UserProfile(
                name = "Test",
                invariants =
                    listOf(
                        ProfileInvariant(
                            category = InvariantCategory.COMMON,
                            description = "Сеть только через Ktor Client",
                            severity = InvariantSeverity.HARD,
                            isActive = true
                        )
                    )
            )

        val prompt = UserProfilePromptBuilder().build(profile)

        assertTrue(prompt.contains("## USER PROFILE"))
        assertTrue(prompt.contains("## ИНВАРИАНТЫ"))
        assertTrue(prompt.contains("### Общие (всегда)"))
        assertTrue(prompt.contains("⛔ Сеть только через Ktor Client"))
    }

    @Test
    fun `build skips inactive invariants`() {
        val profile =
            UserProfile(
                name = "Test",
                invariants =
                    listOf(
                        ProfileInvariant(
                            category = InvariantCategory.SECURITY,
                            description = "Не логировать секреты",
                            severity = InvariantSeverity.HARD,
                            isActive = false
                        )
                    )
            )

        val prompt = UserProfilePromptBuilder().build(profile)

        assertFalse(prompt.contains("## ИНВАРИАНТЫ"))
        assertFalse(prompt.contains("Не логировать секреты"))
    }

    @Test
    fun `build omits displayName when forbidden by hard invariant`() {
        val profile =
            UserProfile(
                name = "Test",
                identity = UserProfileIdentity(displayName = "Данил"),
                invariants =
                    listOf(
                        ProfileInvariant(
                            category = InvariantCategory.COMMON,
                            description = "Никогда не называй имя пользователя",
                            severity = InvariantSeverity.HARD,
                            isActive = true
                        )
                    )
            )

        val prompt = UserProfilePromptBuilder().build(profile)

        assertFalse(prompt.contains("Обращайся к пользователю"))
        assertFalse(prompt.contains("Данил"))
        assertTrue(prompt.contains("## ИНВАРИАНТЫ"))
    }
}
