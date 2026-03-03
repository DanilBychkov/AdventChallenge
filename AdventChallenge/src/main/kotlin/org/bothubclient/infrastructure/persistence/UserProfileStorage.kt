package org.bothubclient.infrastructure.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bothubclient.domain.entity.UserProfile
import org.bothubclient.domain.entity.UserProfileDefaults
import java.nio.file.Path
import kotlin.io.path.*

class UserProfileStorage(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    },
    baseDir: Path = Path.of(System.getProperty("user.home"), ".bothubclient", "profiles")
) {
    private val profilesDir: Path = runCatching {
        baseDir.apply { createDirectories() }
    }.getOrElse {
        Path.of(System.getProperty("java.io.tmpdir"), "bothubclient_profiles").apply {
            createDirectories()
        }
    }

    private val profilesFile: Path = profilesDir / "user_profiles.json"

    suspend fun loadProfiles(): List<UserProfile> = withContext(Dispatchers.IO) {
        if (!profilesFile.exists()) {
            val defaults = UserProfileDefaults.getStarterProfiles()
            saveProfiles(defaults)
            return@withContext defaults
        }

        runCatching {
            val content = profilesFile.readText()
            if (content.isBlank()) {
                val defaults = UserProfileDefaults.getStarterProfiles()
                saveProfiles(defaults)
                return@withContext defaults
            }
            val loaded = json.decodeFromString<List<UserProfile>>(content)
            // Normalize: keep at most one active profile to avoid ambiguous state.
            val activeCount = loaded.count { it.isActive }
            if (activeCount <= 1) return@withContext loaded

            val firstActiveId = loaded.firstOrNull { it.isActive }?.id
            val normalized = loaded.map { it.withActiveStatus(it.id == firstActiveId) }
            runCatching { saveProfiles(normalized) }
            normalized
        }.getOrElse { e ->
            println("[ProfileStorage] Failed to load profiles: ${e.message}")
            UserProfileDefaults.getStarterProfiles()
        }
    }

    suspend fun saveProfiles(profiles: List<UserProfile>) = withContext(Dispatchers.IO) {
        runCatching {
            val content = json.encodeToString(profiles)
            profilesFile.writeText(content)
        }.getOrElse { e ->
            println("[ProfileStorage] Failed to save profiles: ${e.message}")
            throw e
        }
    }

    suspend fun getActiveProfile(): UserProfile? {
        val profiles = loadProfiles()
        return profiles.find { it.isActive }
    }

    /**
     * @param profileId id профиля, который нужно сделать активным.
     * Если null — активный профиль снимается (режим "Без профиля").
     */
    suspend fun setActiveProfile(profileId: String?) {
        val profiles = loadProfiles()
        val updated = profiles.map { profile ->
            profile.withActiveStatus(profileId != null && profile.id == profileId)
        }
        saveProfiles(updated)
    }

    suspend fun addProfile(profile: UserProfile) {
        val profiles = loadProfiles().toMutableList()
        profiles.add(profile)
        saveProfiles(profiles)
    }

    suspend fun updateProfile(profile: UserProfile) {
        val profiles = loadProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            profiles[index] = profile.updated()
            saveProfiles(profiles)
        }
    }

    suspend fun deleteProfile(profileId: String) {
        val profiles = loadProfiles().toMutableList()
        val filtered = profiles.filter { it.id != profileId }
        if (filtered.size < profiles.size) {
            saveProfiles(filtered)
        }
    }
}
