package org.bothubclient.domain.repository

import org.bothubclient.domain.entity.UserProfile

interface UserProfileRepository {
    fun getCachedActiveProfile(): UserProfile?

    suspend fun loadActiveProfile(): UserProfile?

    suspend fun loadActiveProfileOrDefault(): UserProfile

    suspend fun loadProfiles(): List<UserProfile>

    suspend fun upsertAndActivate(profile: UserProfile): UserProfile

    suspend fun setActiveProfile(profileId: String?)
}
