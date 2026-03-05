package org.bothubclient.infrastructure.repository

import org.bothubclient.domain.entity.UserProfile
import org.bothubclient.domain.entity.UserProfileDefaults
import org.bothubclient.infrastructure.persistence.UserProfileStorage
import java.util.concurrent.atomic.AtomicReference
import org.bothubclient.domain.repository.UserProfileRepository as UserProfileRepositoryPort

class UserProfileRepository(
    private val storage: UserProfileStorage
) : UserProfileRepositoryPort {
    private val activeCache = AtomicReference<UserProfile?>(null)

    override fun getCachedActiveProfile(): UserProfile? = activeCache.get()

    /**
     * @return активный профиль, либо null если выбран режим "Без профиля".
     */
    override suspend fun loadActiveProfile(): UserProfile? {
        val active = storage.getActiveProfile()
        activeCache.set(active)
        return active
    }

    /**
     * @return активный профиль, либо [UserProfileDefaults.DEFAULT_PROFILE] если активного нет.
     * Удобно для UI-форм, где нужен "какой-то" профиль по умолчанию.
     */
    override suspend fun loadActiveProfileOrDefault(): UserProfile {
        val fromStorage = storage.getActiveProfile()
        activeCache.set(fromStorage)
        return fromStorage ?: UserProfileDefaults.DEFAULT_PROFILE
    }

    override suspend fun loadProfiles(): List<UserProfile> {
        val profiles = storage.loadProfiles()
        val active = profiles.firstOrNull { it.isActive }
        if (active != null) {
            activeCache.set(active)
        } else {
            activeCache.set(null)
        }
        return profiles
    }

    override suspend fun upsertAndActivate(profile: UserProfile): UserProfile {
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
    override suspend fun setActiveProfile(profileId: String?) {
        storage.setActiveProfile(profileId)
        activeCache.set(storage.getActiveProfile())
    }
}
