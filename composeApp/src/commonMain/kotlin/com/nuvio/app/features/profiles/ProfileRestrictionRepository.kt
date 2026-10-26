package com.nuvio.app.features.profiles

import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.core.storage.ProfileTextStorage
import com.nuvio.app.features.home.HomeUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tmdb.TmdbMetadataService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Better Nuvio: restrição do perfil, como restricao.c na TV (restricao-pN.txt):
 * "Perfil infantil" e "Classificação máxima" na escada Livre/10/12/14/16/18,
 * onde 18 é "Sem limite". Perfil infantil limita a 10 mesmo que a escada diga mais.
 */
@Serializable
data class ProfileRestriction(
    val kids: Boolean = false,
    val maxAge: Int = NO_LIMIT,
) {
    val effectiveMaxAge: Int get() = if (kids && maxAge > KIDS_MAX_AGE) KIDS_MAX_AGE else maxAge
    val isActive: Boolean get() = kids || maxAge < NO_LIMIT

    companion object {
        const val NO_LIMIT = 18
        const val KIDS_MAX_AGE = 10
        val AGE_STEPS = listOf(0, 10, 12, 14, 16, 18)
    }
}

/**
 * Better Nuvio: guarda a restrição por perfil NESTE aparelho (como a TV; o
 * perfil da conta mora no servidor do Nuvio e não tem esse campo) e decide o
 * que some de Home, catálogos, Busca e Explorar. Os itens de catálogo não trazem
 * classificação; a TV tem o pacote com ela e aqui ela vem do TMDB, uma vez por
 * título, guardada em cache. Enquanto a consulta não volta, o item fica de fora
 * num perfil restrito — melhor aparecer depois do que piscar um título 18.
 */
object ProfileRestrictionRepository {
    private const val STORAGE_NAME = "restricao"
    private const val RATINGS_CACHE_PATH = "bn_age_ratings.json"
    private const val MAX_CACHED_RATINGS = 5000

    /** Sem classificação conhecida (TMDB não sabe ou não respondeu). */
    private const val UNKNOWN = -1

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    // Main: o set de pedidos em andamento e o cache só mudam nesta thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val requests = Semaphore(4)

    private var loadedProfileId: Int? = null
    private val _active = MutableStateFlow(ProfileRestriction())
    val active: StateFlow<ProfileRestriction> = _active.asStateFlow()

    // "tipo:id" -> idade; UNKNOWN só fica em memória, para tentar de novo noutra sessão.
    private val _ratings = MutableStateFlow<Map<String, Int>>(emptyMap())
    val ratings: StateFlow<Map<String, Int>> = _ratings.asStateFlow()
    private var ratingsLoaded = false
    private val inFlight = mutableSetOf<String>()
    private var persistJob: Job? = null

    fun ensureLoaded() {
        if (loadedProfileId == ProfileRepository.activeProfileId) return
        onProfileChanged()
    }

    fun onProfileChanged() {
        val profileId = ProfileRepository.activeProfileId
        loadedProfileId = profileId
        _active.value = load(profileId)
    }

    fun clearLocalState() {
        loadedProfileId = null
        _active.value = ProfileRestriction()
    }

    fun load(profileIndex: Int): ProfileRestriction =
        runCatching {
            ProfileTextStorage.load(STORAGE_NAME, profileIndex)
                ?.let { json.decodeFromString<ProfileRestriction>(it) }
        }.getOrNull()?.sanitized() ?: ProfileRestriction()

    fun save(profileIndex: Int, restriction: ProfileRestriction) {
        val clean = restriction.sanitized()
        ProfileTextStorage.save(STORAGE_NAME, profileIndex, json.encodeToString(clean))
        if (profileIndex == ProfileRepository.activeProfileId) {
            loadedProfileId = profileIndex
            _active.value = clean
        }
    }

    private fun ProfileRestriction.sanitized(): ProfileRestriction =
        copy(maxAge = maxAge.coerceIn(0, ProfileRestriction.NO_LIMIT))

    /** restricao_idade: "L", "PG-13", "TV-MA", "16"... na escada de idades; -1 quando não dá para ler. */
    fun ageFromCertification(certification: String?): Int {
        val value = certification?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return UNKNOWN
        when (value) {
            "L", "AL", "LIVRE", "G", "TV-Y", "TV-G", "U", "0" -> return 0
            "PG", "TV-Y7", "TV-Y7-FV", "TV-PG" -> return 10
            "PG-13" -> return 12
            "TV-14" -> return 14
            "R" -> return 16
            "NC-17", "TV-MA", "X" -> return 18
        }
        val digits = value.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }
        val number = digits.toIntOrNull() ?: return UNKNOWN
        return if (number in 0..21) minOf(number, 18) else UNKNOWN
    }

    private val familyGenreMarkers = listOf("anima", "fam", "infantil", "kids", "children", "desenho")

    /** generoFamilia: sem classificação, o perfil infantil só vê animação/família/infantil. */
    fun isFamilyGenre(genres: List<String>): Boolean =
        genres.any { genre ->
            val lower = genre.lowercase()
            familyGenreMarkers.any { lower.contains(it) }
        }

    /** restricao_bloqueia, com a idade já lida. */
    private fun blocksAge(age: Int, genres: List<String>, restriction: ProfileRestriction): Boolean {
        if (!restriction.isActive) return false
        if (age < 0) return restriction.kids && !isFamilyGenre(genres)
        return age > restriction.effectiveMaxAge
    }

    fun blocksCertification(
        certification: String?,
        genres: List<String>,
        restriction: ProfileRestriction = active.value,
    ): Boolean = blocksAge(ageFromCertification(certification), genres, restriction)

    /**
     * Para abrir um título: a classificação do detalhe vale mais; sem ela, a do
     * cache. Devolve true quando o perfil não pode abrir.
     */
    fun blocksTitle(
        type: String,
        id: String,
        certification: String?,
        genres: List<String>,
        restriction: ProfileRestriction = active.value,
        knownRatings: Map<String, Int> = ratings.value,
    ): Boolean {
        if (!restriction.isActive) return false
        val fromMeta = ageFromCertification(certification)
        if (fromMeta >= 0) return blocksAge(fromMeta, genres, restriction)
        val cached = knownRatings[key(type, id)] ?: UNKNOWN
        return blocksAge(cached, genres, restriction)
    }

    fun isBlocked(
        item: MetaPreview,
        restriction: ProfileRestriction = active.value,
        knownRatings: Map<String, Int> = ratings.value,
    ): Boolean {
        if (!restriction.isActive) return false
        // Ainda sem resposta do TMDB: fica de fora até a classificação chegar.
        val age = knownRatings[key(item.type, item.id)] ?: return true
        return blocksAge(age, item.genres, restriction)
    }

    fun filter(
        items: List<MetaPreview>,
        restriction: ProfileRestriction = active.value,
        knownRatings: Map<String, Int> = ratings.value,
    ): List<MetaPreview> {
        if (!restriction.isActive) return items
        return items.filterNot { isBlocked(it, restriction, knownRatings) }
    }

    /** Home: tira das fileiras e do destaque o que o perfil não vê; fileira vazia some. */
    fun filterHome(
        state: HomeUiState,
        restriction: ProfileRestriction = active.value,
        knownRatings: Map<String, Int> = ratings.value,
    ): HomeUiState {
        if (!restriction.isActive) return state
        return state.copy(
            heroItems = filter(state.heroItems, restriction, knownRatings),
            sections = state.sections.mapNotNull { section ->
                val allowed = filter(section.items, restriction, knownRatings)
                if (allowed.isEmpty()) null
                else section.copy(items = allowed, availableItemCount = allowed.size)
            },
        )
    }

    /** Pede ao TMDB a classificação dos itens que ainda não têm (só num perfil restrito). */
    fun request(items: List<MetaPreview>) {
        if (!active.value.isActive || items.isEmpty()) return
        loadRatingsCacheIfNeeded()
        val known = _ratings.value
        val pending = items
            .asSequence()
            .map { key(it.type, it.id) to it }
            .filter { (k, _) -> k !in known }
            .distinctBy { it.first }
            .toList()
        if (pending.isEmpty()) return
        pending.forEach { (k, item) ->
            if (!inFlight.add(k)) return@forEach
            scope.launch {
                val age = requests.withPermit {
                    runCatching { TmdbMetadataService.fetchAgeRating(item.id, item.type) }
                        .getOrNull()
                        .let(::ageFromCertification)
                }
                inFlight.remove(k)
                _ratings.value = _ratings.value + (k to age)
                if (age >= 0) schedulePersist()
            }
        }
    }

    /** A classificação que o detalhe trouxe entra no cache, e o título passa a valer nas fileiras. */
    fun recordCertification(type: String, id: String, certification: String?) {
        val age = ageFromCertification(certification)
        if (age < 0) return
        loadRatingsCacheIfNeeded()
        val k = key(type, id)
        if (_ratings.value[k] == age) return
        _ratings.value = _ratings.value + (k to age)
        schedulePersist()
    }

    private fun key(type: String, id: String): String = "${type.lowercase()}:$id"

    private fun loadRatingsCacheIfNeeded() {
        if (ratingsLoaded) return
        ratingsLoaded = true
        val stored = runCatching {
            AppFileCache.read(RATINGS_CACHE_PATH)
                ?.takeIf(String::isNotBlank)
                ?.let { json.decodeFromString<Map<String, Int>>(it) }
        }.getOrNull().orEmpty()
        if (stored.isNotEmpty()) _ratings.value = stored + _ratings.value
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(1500)
            val known = _ratings.value.filterValues { it >= 0 }
            val trimmed = if (known.size > MAX_CACHED_RATINGS) {
                known.entries.toList().takeLast(MAX_CACHED_RATINGS).associate { it.key to it.value }
            } else {
                known
            }
            val payload = json.encodeToString(trimmed)
            withContext(Dispatchers.Default) {
                runCatching { AppFileCache.write(RATINGS_CACHE_PATH, payload) }
            }
        }
    }
}
