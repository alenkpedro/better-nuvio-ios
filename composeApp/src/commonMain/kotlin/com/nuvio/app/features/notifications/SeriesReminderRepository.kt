package com.nuvio.app.features.notifications

import com.nuvio.app.core.storage.ProfileTextStorage
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Better Nuvio: uma série com "Lembrar-me" ligado (lembretes-pN.txt na TV). */
@Serializable
data class SeriesReminder(
    val id: String,
    val type: String,
    val name: String,
    /** Dia em que o lembrete foi ligado: só os episódios daqui em diante avisam. */
    val sinceIsoDate: String,
)

/**
 * Better Nuvio: "Lembrar-me" da página do título, como agenda.c na TV, por série e
 * por perfil. Na TV não há push e o aviso aparece quando o app abre; aqui o
 * lembrete entra no agendamento de notificações locais de novos episódios
 * (EpisodeReleaseNotificationsRepository), mesmo com o aviso geral da biblioteca
 * desligado nos ajustes.
 */
object SeriesReminderRepository {
    private const val STORAGE_NAME = "series_reminders"
    private const val MAX_ITEMS = 120
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadedProfileId: Int? = null
    private val state = MutableStateFlow<List<SeriesReminder>>(emptyList())
    val uiState: StateFlow<List<SeriesReminder>> = state.asStateFlow()

    fun ensureLoaded() {
        if (loadedProfileId == ProfileRepository.activeProfileId) return
        onProfileChanged()
    }

    fun onProfileChanged() {
        val profileId = ProfileRepository.activeProfileId
        loadedProfileId = profileId
        state.value = runCatching {
            ProfileTextStorage.load(STORAGE_NAME, profileId)
                ?.let { json.decodeFromString<List<SeriesReminder>>(it) }
        }.getOrNull().orEmpty()
    }

    fun clearLocalState() {
        loadedProfileId = null
        state.value = emptyList()
    }

    /** "tt123:1:2" e "tt123" são a mesma série. */
    private fun baseId(id: String): String = id.substringBefore(':').ifBlank { id }

    fun isActive(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        ensureLoaded()
        val base = baseId(id)
        return state.value.any { baseId(it.id) == base }
    }

    internal fun trackedKeys(): Set<String> {
        ensureLoaded()
        return state.value.mapTo(mutableSetOf()) { buildTrackedShowKey(it.type, it.id) }
    }

    /**
     * Liga ou desliga o lembrete. Ligar pede a permissão de notificação; sem ela o
     * lembrete não entra e [onResult] recebe false com `denied = true`.
     */
    fun toggle(meta: MetaDetails, onResult: (active: Boolean, denied: Boolean) -> Unit = { _, _ -> }) {
        ensureLoaded()
        if (isActive(meta.id)) {
            val base = baseId(meta.id)
            persist(state.value.filterNot { baseId(it.id) == base })
            EpisodeReleaseNotificationsRepository.refreshAsync()
            onResult(false, false)
            return
        }
        scope.launch {
            val granted = runCatching { EpisodeReleaseNotificationPlatform.requestAuthorization() }.getOrDefault(false)
            if (!granted) {
                onResult(false, true)
                return@launch
            }
            val reminder = SeriesReminder(
                id = baseId(meta.id),
                type = meta.type,
                name = meta.name,
                sinceIsoDate = CurrentDateProvider.todayIsoDate(),
            )
            persist((state.value + reminder).takeLast(MAX_ITEMS))
            EpisodeReleaseNotificationsRepository.refreshAsync()
            onResult(true, false)
        }
    }

    private fun persist(items: List<SeriesReminder>) {
        state.value = items
        val profileId = loadedProfileId ?: ProfileRepository.activeProfileId
        ProfileTextStorage.save(STORAGE_NAME, profileId, json.encodeToString(items))
    }
}

/**
 * O botão só existe em série com episódio por vir (agenda_pode_lembrar na TV), ou
 * quando o lembrete já está ligado, para poder desligar.
 */
fun MetaDetails.canRemindNewEpisodes(): Boolean {
    if (!isSeriesLibraryType(type)) return false
    if (SeriesReminderRepository.isActive(id)) return true
    val today = CurrentDateProvider.todayIsoDate()
    return videos.any { video -> releaseDateIso(video.released)?.let { it >= today } == true }
}
