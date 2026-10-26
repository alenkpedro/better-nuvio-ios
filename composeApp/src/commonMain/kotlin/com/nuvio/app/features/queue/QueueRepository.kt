package com.nuvio.app.features.queue

import com.nuvio.app.core.storage.ProfileTextStorage
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Better Nuvio: um título da fila. A TV guarda só o id (fila-pN.txt) e acha o resto
 * no catálogo; aqui não há catálogo local, então o item leva nome e pôster junto.
 */
@Serializable
data class QueueItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val banner: String? = null,
    val logo: String? = null,
    val releaseInfo: String? = null,
    val addedAtEpochMs: Long = 0L,
)

@Serializable
data class QueueProfileState(
    val items: List<QueueItem> = emptyList(),
    /** Modo maratona: pula créditos e resumos; o próximo começa sozinho. */
    val bingeMode: Boolean = false,
)

/**
 * Better Nuvio: a fila ("Fila") por perfil, como fila.c na TV. Local, como na TV: a
 * sincronização da conta não tem onde guardar uma fila, e o ganho não paga um esquema novo.
 */
object QueueRepository {
    private const val STORAGE_NAME = "queue"
    private const val MAX_ITEMS = 100
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var loadedProfileId: Int? = null
    private val state = MutableStateFlow(QueueProfileState())
    val uiState: StateFlow<QueueProfileState> = state.asStateFlow()

    fun ensureLoaded() {
        if (loadedProfileId == ProfileRepository.activeProfileId) return
        onProfileChanged()
    }

    fun onProfileChanged() {
        val profileId = ProfileRepository.activeProfileId
        loadedProfileId = profileId
        state.value = runCatching {
            ProfileTextStorage.load(STORAGE_NAME, profileId)
                ?.let { json.decodeFromString<QueueProfileState>(it) }
        }.getOrNull() ?: QueueProfileState()
    }

    fun clearLocalState() {
        loadedProfileId = null
        state.value = QueueProfileState()
    }

    /** "tt123:1:2" e "tt123" são o mesmo título na fila, como o base() da TV. */
    fun baseId(id: String): String = id.substringBefore(':').ifBlank { id }

    fun contains(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        ensureLoaded()
        val base = baseId(id)
        return state.value.items.any { it.id == base }
    }

    /** Liga/desliga o título na fila; devolve true quando ele entrou. */
    fun toggle(item: QueueItem): Boolean {
        if (contains(item.id)) {
            remove(item.id)
            return false
        }
        return add(item)
    }

    fun add(item: QueueItem): Boolean {
        ensureLoaded()
        val base = baseId(item.id)
        val current = state.value
        if (current.items.any { it.id == base } || current.items.size >= MAX_ITEMS) return false
        val stamped = item.copy(
            id = base,
            addedAtEpochMs = item.addedAtEpochMs.takeIf { it > 0L } ?: WatchProgressClock.nowEpochMs(),
        )
        persist(current.copy(items = current.items + stamped))
        return true
    }

    fun remove(id: String) {
        ensureLoaded()
        val base = baseId(id)
        val current = state.value
        if (current.items.none { it.id == base }) return
        persist(current.copy(items = current.items.filterNot { it.id == base }))
    }

    fun move(fromIndex: Int, toIndex: Int) {
        ensureLoaded()
        val items = state.value.items.toMutableList()
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return
        items.add(toIndex, items.removeAt(fromIndex))
        persist(state.value.copy(items = items))
    }

    fun clear() {
        ensureLoaded()
        persist(state.value.copy(items = emptyList()))
    }

    fun setBingeMode(enabled: Boolean) {
        ensureLoaded()
        persist(state.value.copy(bingeMode = enabled))
    }

    val bingeMode: Boolean
        get() {
            ensureLoaded()
            return state.value.bingeMode
        }

    /** O próximo da fila que não é o título tocando agora (fila_proximo_indice na TV). */
    fun nextAfter(currentId: String?): QueueItem? {
        ensureLoaded()
        val base = currentId?.let(::baseId)
        return state.value.items.firstOrNull { it.id != base }
    }

    private fun persist(next: QueueProfileState) {
        val profileId = ProfileRepository.activeProfileId
        state.value = next
        loadedProfileId = profileId
        runCatching { ProfileTextStorage.save(STORAGE_NAME, profileId, json.encodeToString(next)) }
    }
}

fun MetaDetails.toQueueItem(): QueueItem = QueueItem(
    id = id,
    type = type,
    name = name,
    poster = poster,
    banner = background,
    logo = logo,
    releaseInfo = releaseInfo,
)

fun MetaPreview.toQueueItem(): QueueItem = QueueItem(
    id = id,
    type = type,
    name = name,
    poster = poster,
    banner = banner,
    logo = logo,
    releaseInfo = releaseInfo,
)

fun QueueItem.toMetaPreview(): MetaPreview = MetaPreview(
    id = id,
    type = type,
    name = name,
    poster = poster,
    banner = banner,
    logo = logo,
    posterShape = PosterShape.Poster,
    releaseInfo = releaseInfo,
)

fun QueueItem.toLibraryItem(): LibraryItem = LibraryItem(
    id = id,
    type = type,
    name = name,
    poster = poster,
    banner = banner,
    logo = logo,
    releaseInfo = releaseInfo,
    imdbId = id.takeIf { it.startsWith("tt") },
    savedAtEpochMs = addedAtEpochMs,
)

internal fun QueueItem.isSeriesType(): Boolean =
    type.lowercase() in setOf("series", "tv", "show", "tvshow", "anime")
