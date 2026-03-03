package org.bothubclient.infrastructure.repository

import org.bothubclient.domain.entity.UserProfile
import org.bothubclient.domain.entity.UserProfileDefaults
import org.bothubclient.infrastructure.persistence.UserProfileStorage
import java.util.concurrent.atomic.AtomicReference

class UserProfileRepository(
    private val storage: UserProfileStorage
) {
    private val activeCache = AtomicReference<UserProfile?>(null)

    fun getCachedActiveProfile(): UserProfile? = activeCache.get()

    /**
     * @return активный профиль, либо null если выбран режим "Без профиля".
     */
    suspend fun loadActiveProfile(): UserProfile? {
        val active = storage.getActiveProfile()
        activeCache.set(active)
        return active
    }

    /**
     * @return активный профиль, либо [UserProfileDefaults.DEFAULT_PROFILE] если активного нет.
     * Удобно для UI-форм, где нужен "какой-то" профиль по умолчанию.
     */
    suspend fun loadActiveProfileOrDefault(): UserProfile {
        val fromStorage = storage.getActiveProfile()
        activeCache.set(fromStorage)
        return fromStorage ?: UserProfileDefaults.DEFAULT_PROFILE
    }

    suspend fun loadProfiles(): List<UserProfile> {
        val profiles = storage.loadProfiles()
        val active = profiles.firstOrNull { it.isActive }
        if (active != null) {
            activeCache.set(active)
        } else {
            activeCache.set(null)
        }
        return profiles
    }

    suspend fun upsertAndActivate(profile: UserProfile): UserProfile {
        val normalized = profile.withActiveStatus(true)
        val existing = storage.loadProfiles()
        val exists = existing.any { it.id == normalized.id }
        if (exists) {
            storage.updateProfile(normalized)
        } else {
            storage.addProfile(normalized)
        }
        storage.setActiveProfile(normalized.id)
        activeCache.set(normalized)
        return normalized
    }

    /**
     * @param profileId id профиля или null для режима "Без профиля"
     */
    suspend fun setActiveProfile(profileId: String?) {
        storage.setActiveProfile(profileId)
        activeCache.set(storage.getActiveProfile())
    }
}
