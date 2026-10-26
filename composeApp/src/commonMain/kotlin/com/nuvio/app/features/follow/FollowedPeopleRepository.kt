package com.nuvio.app.features.follow

import com.nuvio.app.core.storage.ProfileTextStorage
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Better Nuvio: uma pessoa seguida (seguindo-pN.txt na TV guarda id e nome). */
@Serializable
data class FollowedPerson(
    val tmdbId: Int,
    val name: String,
    val photo: String? = null,
    val followedAtEpochMs: Long = 0L,
)

/**
 * Better Nuvio: "Seguir" da página da pessoa, como seguir.c na TV: uma lista por
 * perfil, até 256 pessoas, só local. Sem aviso de título novo — a TV também não
 * avisa, e o app não tem como olhar os créditos do TMDB em segundo plano.
 */
object FollowedPeopleRepository {
    private const val STORAGE_NAME = "followed_people"
    private const val MAX_ITEMS = 256
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var loadedProfileId: Int? = null
    private val state = MutableStateFlow<List<FollowedPerson>>(emptyList())
    val uiState: StateFlow<List<FollowedPerson>> = state.asStateFlow()

    fun ensureLoaded() {
        if (loadedProfileId == ProfileRepository.activeProfileId) return
        onProfileChanged()
    }

    fun onProfileChanged() {
        val profileId = ProfileRepository.activeProfileId
        loadedProfileId = profileId
        state.value = runCatching {
            ProfileTextStorage.load(STORAGE_NAME, profileId)
                ?.let { json.decodeFromString<List<FollowedPerson>>(it) }
        }.getOrNull().orEmpty()
    }

    fun clearLocalState() {
        loadedProfileId = null
        state.value = emptyList()
    }

    fun isFollowing(tmdbId: Int): Boolean {
        ensureLoaded()
        return state.value.any { it.tmdbId == tmdbId }
    }

    /** Liga/desliga; devolve true quando passou a seguir. */
    fun toggle(tmdbId: Int, name: String, photo: String?): Boolean {
        if (tmdbId <= 0) return false
        ensureLoaded()
        val current = state.value
        if (current.any { it.tmdbId == tmdbId }) {
            persist(current.filterNot { it.tmdbId == tmdbId })
            return false
        }
        if (current.size >= MAX_ITEMS) return false
        persist(
            current + FollowedPerson(
                tmdbId = tmdbId,
                name = name.replace('\t', ' ').replace('\n', ' ').trim(),
                photo = photo,
                followedAtEpochMs = WatchProgressClock.nowEpochMs(),
            ),
        )
        return true
    }

    private fun persist(items: List<FollowedPerson>) {
        state.value = items
        val profileId = loadedProfileId ?: ProfileRepository.activeProfileId
        ProfileTextStorage.save(STORAGE_NAME, profileId, json.encodeToString(items))
    }
}
