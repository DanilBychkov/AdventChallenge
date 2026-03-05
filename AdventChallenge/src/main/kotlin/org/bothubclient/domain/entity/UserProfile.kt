package org.bothubclient.domain.entity

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class ProfileInvariant(
    val id: String = UUID.randomUUID().toString(),
    val category: InvariantCategory,
    val description: String,
    val rationale: String = "",
    val severity: InvariantSeverity = InvariantSeverity.HARD,
    val isActive: Boolean = true
)

@Serializable
enum class InvariantCategory {
    COMMON,
    ARCHITECTURE,
    TECH_STACK,
    BUSINESS_RULES,
    SECURITY,
    CODE_STYLE,
    CONSTRAINTS
}

@Serializable
enum class InvariantSeverity {
    HARD,
    SOFT,
    ADVISORY
}

@Serializable
data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "User",
    val isActive: Boolean = false,
    val identity: UserProfileIdentity = UserProfileIdentity(),
    val preferences: UserPreferences = UserPreferences(),
    val behaviorRules: List<BehaviorRule> = emptyList(),
    val context: UserContext = UserContext(),
    val invariants: List<ProfileInvariant> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun withActiveStatus(active: Boolean): UserProfile {
        return copy(
            isActive = active,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun updated(): UserProfile {
        return copy(updatedAt = System.currentTimeMillis())
    }
}

@Serializable
data class UserProfileIdentity(
    val displayName: String = "",
    val role: String = "",
    val expertiseAreas: List<String> = emptyList()
)

@Serializable
data class UserPreferences(
    val communicationStyle: CommunicationStyle = CommunicationStyle(),
    val responseFormat: ResponseFormat = ResponseFormat(),
    val language: LanguagePrefs = LanguagePrefs(),
    val technicalLevel: TechnicalLevel = TechnicalLevel.INTERMEDIATE
)

@Serializable
data class BehaviorRule(
    val condition: String,
    val action: String,
    val priority: Int = 0
)

@Serializable
data class UserContext(
    val projectContext: String = "",
    val companyContext: String = "",
    val currentGoals: List<String> = emptyList(),
    val avoidedTopics: List<String> = emptyList(),
    val preferredTechnologies: Map<String, List<String>> = emptyMap()
)

@Serializable
data class CommunicationStyle(
    val formality: FormalityLevel = FormalityLevel.NEUTRAL,
    val verbosity: VerbosityLevel = VerbosityLevel.MODERATE,
    val tone: ToneStyle = ToneStyle.FRIENDLY_PROFESSIONAL
)

@Serializable
data class ResponseFormat(
    val useMarkdown: Boolean = true,
    val preferLists: Boolean = true,
    val codeBlockStyle: CodeBlockStyle = CodeBlockStyle.WITH_EXPLANATION,
    val includeSummaries: Boolean = true
)

@Serializable
data class LanguagePrefs(
    val primaryLanguage: String = "ru",
    val codeCommentsLanguage: String = "en",
    val translateTechnicalTerms: Boolean = false
)

@Serializable
enum class TechnicalLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    EXPERT
}

@Serializable
enum class FormalityLevel {
    CASUAL,
    NEUTRAL,
    FORMAL
}

@Serializable
enum class VerbosityLevel {
    CONCISE,
    MODERATE,
    DETAILED
}

@Serializable
enum class ToneStyle {
    NEUTRAL,
    FRIENDLY,
    FRIENDLY_PROFESSIONAL,
    STRICT_PROFESSIONAL
}

@Serializable
enum class CodeBlockStyle {
    MINIMAL,
    WITH_COMMENTS,
    WITH_EXPLANATION,
    FULL_DOCUMENTATION
}

object UserProfileDefaults {
    val SOLO_CODER_INVARIANTS =
        listOf(
            ProfileInvariant(
                category = InvariantCategory.ARCHITECTURE,
                description = "Clean Architecture: domain не зависит от infrastructure/presentation",
                rationale = "Обратные зависимости нарушают тестируемость",
                severity = InvariantSeverity.HARD
            ),
            ProfileInvariant(
                category = InvariantCategory.TECH_STACK,
                description = "UI только на JetBrains Compose Desktop (не JavaFX, не Swing)",
                severity = InvariantSeverity.HARD
            ),
            ProfileInvariant(
                category = InvariantCategory.TECH_STACK,
                description = "Сеть только через Ktor Client",
                severity = InvariantSeverity.HARD
            ),
            ProfileInvariant(
                category = InvariantCategory.SECURITY,
                description = "API ключи только через переменные окружения, никогда в коде",
                severity = InvariantSeverity.HARD
            ),
            ProfileInvariant(
                category = InvariantCategory.CONSTRAINTS,
                description = "Kotlin/JVM target — без мультиплатформы",
                severity = InvariantSeverity.SOFT
            )
        )

    val DEFAULT_PROFILE = UserProfile(
        id = "default",
        name = "Default",
        isActive = true
    )

    val SOLO_CODER_PROFILE =
        UserProfile(
            id = UUID.randomUUID().toString(),
            name = "Solo Coder",
            identity =
                UserProfileIdentity(
                    displayName = "",
                    role = "Kotlin-разработчик",
                    expertiseAreas = listOf("Kotlin", "Compose Desktop", "Ktor", "Clean Architecture")
                ),
            preferences =
                UserPreferences(
                    communicationStyle =
                        CommunicationStyle(
                            formality = FormalityLevel.NEUTRAL,
                            verbosity = VerbosityLevel.MODERATE,
                            tone = ToneStyle.FRIENDLY_PROFESSIONAL
                        ),
                    responseFormat =
                        ResponseFormat(
                            useMarkdown = true,
                            preferLists = true,
                            codeBlockStyle = CodeBlockStyle.WITH_EXPLANATION,
                            includeSummaries = true
                        ),
                    language =
                        LanguagePrefs(
                            primaryLanguage = "ru",
                            codeCommentsLanguage = "en",
                            translateTechnicalTerms = false
                        ),
                    technicalLevel = TechnicalLevel.INTERMEDIATE
                ),
            context =
                UserContext(
                    projectContext = "AdventChallenge (Compose Desktop, Clean Architecture)",
                    preferredTechnologies =
                        mapOf(
                            "language" to listOf("Kotlin"),
                            "ui" to listOf("JetBrains Compose Desktop"),
                            "network" to listOf("Ktor Client")
                        )
                ),
            invariants = SOLO_CODER_INVARIANTS
        )

    val DEVELOPER_PROFILE =
        UserProfile(
            id = UUID.randomUUID().toString(),
            name = "Developer",
            identity =
                UserProfileIdentity(
                    displayName = "Developer",
                    role = "Software Developer",
                    expertiseAreas = listOf("architecture", "debugging", "refactoring")
                ),
            preferences =
                UserPreferences(
                    communicationStyle =
                        CommunicationStyle(
                            formality = FormalityLevel.NEUTRAL,
                            verbosity = VerbosityLevel.DETAILED,
                            tone = ToneStyle.STRICT_PROFESSIONAL
                        ),
                    responseFormat =
                        ResponseFormat(
                            useMarkdown = true,
                            preferLists = true,
                            codeBlockStyle = CodeBlockStyle.WITH_EXPLANATION,
                            includeSummaries = true
                        ),
                    language =
                        LanguagePrefs(
                            primaryLanguage = "en",
                            codeCommentsLanguage = "en",
                            translateTechnicalTerms = false
                        ),
                    technicalLevel = TechnicalLevel.ADVANCED
                ),
            behaviorRules =
                listOf(
                    BehaviorRule(
                        condition = "вносишь изменения в код",
                        action = "объясняй мотивацию и влияние изменений",
                        priority = 80
                    ),
                    BehaviorRule(
                        condition = "пишешь код",
                        action = "соблюдай существующий стиль и архитектуру проекта",
                        priority = 100
                    )
                )
        )

    fun getStarterProfiles(): List<UserProfile> {
        return listOf(
            DEFAULT_PROFILE,
            SOLO_CODER_PROFILE.copy(id = UUID.randomUUID().toString()),
            DEVELOPER_PROFILE.copy(id = UUID.randomUUID().toString())
        )
    }
}
