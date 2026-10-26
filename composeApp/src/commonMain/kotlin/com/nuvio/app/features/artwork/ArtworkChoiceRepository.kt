package com.nuvio.app.features.artwork

import com.nuvio.app.core.storage.ProfileTextStorage
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Better Nuvio: a arte escolhida a mão para um título; null = Automático. */
@Serializable
data class ArtworkChoice(
    val background: String? = null,
    val logo: String? = null,
) {
    val isEmpty: Boolean get() = background.isNullOrBlank() && logo.isNullOrBlank()
}

@Serializable
private data class StoredArtworkChoices(
    /** Na ordem em que foram escolhidas; cheia, a mais antiga sai (arteescolha.h). */
    val entries: List<Pair<String, ArtworkChoice>> = emptyList(),
)

/**
 * Better Nuvio: "Trocar arte" (arteescolha.c na TV). Fundo e logo escolhidos por
 * título e por perfil; valem na página do título e no destaque da Home. A chave
 * é o título, não o episódio: "tt0903747:2:3" vira "tt0903747" e
 * "kitsu:7442:3" vira "kitsu:7442".
 */
object ArtworkChoiceRepository {
    private const val STORAGE_NAME = "artwork_choice"
    private const val MAX_ITEMS = 160
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var loadedProfileId: Int? = null
    private val state = MutableStateFlow<Map<String, ArtworkChoice>>(emptyMap())
    val uiState: StateFlow<Map<String, ArtworkChoice>> = state.asStateFlow()

    fun ensureLoaded() {
        if (loadedProfileId == ProfileRepository.activeProfileId) return
        onProfileChanged()
    }

    fun onProfileChanged() {
        val profileId = ProfileRepository.activeProfileId
        loadedProfileId = profileId
        state.value = runCatching {
            ProfileTextStorage.load(STORAGE_NAME, profileId)
                ?.let { json.decodeFromString<StoredArtworkChoices>(it) }
                ?.entries
                ?.toMap(LinkedHashMap())
        }.getOrNull() ?: emptyMap()
    }

    fun clearLocalState() {
        loadedProfileId = null
        state.value = emptyMap()
    }

    fun key(id: String): String {
        val parts = id.trim().split(':')
        return when {
            parts.size <= 1 -> id.trim()
            parts[0].startsWith("tt") -> parts[0]
            else -> parts[0] + ":" + parts[1]
        }
    }

    fun choiceFor(id: String?): ArtworkChoice? {
        if (id.isNullOrBlank()) return null
        ensureLoaded()
        return state.value[key(id)]
    }

    fun backgroundFor(id: String?): String? = choiceFor(id)?.background?.takeIf { it.isNotBlank() }
    fun logoFor(id: String?): String? = choiceFor(id)?.logo?.takeIf { it.isNotBlank() }

    /** `url` null = volta ao Automático. */
    fun setBackground(id: String, url: String?) = update(id) { it.copy(background = url?.takeIf(String::isNotBlank)) }
    fun setLogo(id: String, url: String?) = update(id) { it.copy(logo = url?.takeIf(String::isNotBlank)) }

    private fun update(id: String, change: (ArtworkChoice) -> ArtworkChoice) {
        ensureLoaded()
        val k = key(id)
        if (k.isBlank()) return
        val current = LinkedHashMap(state.value)
        val next = change(current.remove(k) ?: ArtworkChoice())
        if (!next.isEmpty) current[k] = next
        while (current.size > MAX_ITEMS) current.remove(current.keys.first())
        state.value = current
        val profileId = loadedProfileId ?: ProfileRepository.activeProfileId
        ProfileTextStorage.save(
            STORAGE_NAME,
            profileId,
            json.encodeToString(StoredArtworkChoices.serializer(), StoredArtworkChoices(current.toList())),
        )
    }
}

/** A meta com a arte escolhida no lugar do fundo e do logo automáticos. */
fun MetaDetails.withArtworkChoice(choices: Map<String, ArtworkChoice>): MetaDetails {
    val choice = choices[ArtworkChoiceRepository.key(id)] ?: return this
    return copy(
        background = choice.background?.takeIf { it.isNotBlank() } ?: background,
        logo = choice.logo?.takeIf { it.isNotBlank() } ?: logo,
    )
}

fun MetaPreview.withArtworkChoice(choices: Map<String, ArtworkChoice>): MetaPreview {
    val choice = choices[ArtworkChoiceRepository.key(id)] ?: return this
    return copy(
        banner = choice.background?.takeIf { it.isNotBlank() } ?: banner,
        logo = choice.logo?.takeIf { it.isNotBlank() } ?: logo,
    )
}
