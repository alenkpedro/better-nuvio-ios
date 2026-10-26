package com.nuvio.app.features.library

import com.nuvio.app.core.storage.ProfileTextStorage
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

/** Segundos assistidos por dia ("AAAA-MM-DD"), como o tempo-dia-pN.txt da TV. */
@Serializable
data class WatchStatsState(
    val secondsByDay: Map<String, Long> = emptyMap(),
)

/**
 * Better Nuvio: o contador diário do Histórico (historico.c na TV). O player soma o tempo
 * que passou tocando; grava a cada minuto de vídeo, não a cada passo do progresso.
 */
object WatchStatsRepository {
    private const val STORAGE_NAME = "watch_seconds_by_day"
    private const val MAX_DAYS = 400
    private const val MAX_TICK_MS = 60_000L
    private const val PERSIST_EVERY_MS = 60_000L
    private const val PERSIST_IDLE_MS = 300_000L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var loadedProfileId: Int? = null
    private var pendingMs = 0L
    private var carriedMs = 0L
    private var lastPersistEpochMs = 0L
    private val state = MutableStateFlow(WatchStatsState())
    val uiState: StateFlow<WatchStatsState> = state.asStateFlow()

    fun ensureLoaded() {
        if (loadedProfileId == ProfileRepository.activeProfileId) return
        onProfileChanged()
    }

    fun onProfileChanged() {
        flush()
        val profileId = ProfileRepository.activeProfileId
        loadedProfileId = profileId
        pendingMs = 0L
        carriedMs = 0L
        state.value = runCatching {
            ProfileTextStorage.load(STORAGE_NAME, profileId)?.let { json.decodeFromString<WatchStatsState>(it) }
        }.getOrNull() ?: WatchStatsState()
    }

    fun clearLocalState() {
        loadedProfileId = null
        pendingMs = 0L
        carriedMs = 0L
        state.value = WatchStatsState()
    }

    /** Um passo do player; passos acima de 1 min (pausa, app suspenso) não contam, como na TV. */
    fun addPlayedMs(ms: Long) {
        if (ms <= 0L || ms > MAX_TICK_MS) return
        ensureLoaded()
        carriedMs += ms
        val wholeSeconds = carriedMs / 1_000L
        if (wholeSeconds <= 0L) return
        carriedMs -= wholeSeconds * 1_000L
        val day = todayLocalDate().toString()
        val days = state.value.secondsByDay.toMutableMap()
        days[day] = (days[day] ?: 0L) + wholeSeconds
        while (days.size > MAX_DAYS) days.remove(days.keys.min())
        state.value = WatchStatsState(days)
        pendingMs += wholeSeconds * 1_000L
        val now = WatchProgressClock.nowEpochMs()
        if (pendingMs >= PERSIST_EVERY_MS || now - lastPersistEpochMs > PERSIST_IDLE_MS) flush()
    }

    fun flush() {
        val profileId = loadedProfileId ?: return
        if (pendingMs <= 0L) return
        pendingMs = 0L
        lastPersistEpochMs = WatchProgressClock.nowEpochMs()
        runCatching { ProfileTextStorage.save(STORAGE_NAME, profileId, json.encodeToString(state.value)) }
    }
}

internal fun todayLocalDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

internal fun localDateTimeOf(epochMs: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())

internal const val WATCH_HISTORY_DAYS = 14
internal const val WATCH_HISTORY_FEED_LIMIT = 150

internal data class WatchHistoryDay(val date: LocalDate, val minutes: Int)

internal data class WatchHistoryStats(
    val days: List<WatchHistoryDay>,
    val minutesThisMonth: Int,
    val minutesLastMonth: Int,
    val streakDays: Int,
    val recordDays: Int,
    val finishedMovies: Int,
    val finishedEpisodes: Int,
)

internal data class WatchHistoryFeedEntry(
    val entry: WatchProgressEntry,
    val date: LocalDate,
    val time: LocalDateTime,
    val percent: Int,
)

/**
 * As contas do historico_calcular da TV: minutos por dia (o contador manda; sem ele, a
 * estimativa pelo progresso), mês atual contra o anterior, sequência e recorde de dias, e
 * concluídos no mês. Os gêneros da TV ficam de fora: o progresso daqui não traz gênero.
 */
internal fun computeWatchHistoryStats(
    entries: List<WatchProgressEntry>,
    secondsByDay: Map<String, Long>,
    today: LocalDate,
): WatchHistoryStats {
    val monthKey = today.toString().take(7)
    val lastMonthKey = today.minus(1, DateTimeUnit.MONTH).toString().take(7)
    val estimateByDay = mutableMapOf<LocalDate, Double>()
    val activeDays = mutableSetOf<LocalDate>()
    var estimateMonth = 0.0
    var estimateLastMonth = 0.0
    var movies = 0
    var episodes = 0

    secondsByDay.forEach { (day, seconds) ->
        if (seconds >= 60L) runCatching { LocalDate.parse(day) }.getOrNull()?.let(activeDays::add)
    }
    entries.forEach { entry ->
        if (entry.lastUpdatedEpochMs <= 0L) return@forEach
        val date = localDateTimeOf(entry.lastUpdatedEpochMs).date
        val completed = entry.isEffectivelyCompleted
        val watchedSeconds = when {
            completed && entry.durationMs > 0L -> entry.durationMs / 1_000.0
            entry.lastPositionMs > 0L -> entry.lastPositionMs / 1_000.0
            else -> entry.progressFraction * entry.durationMs / 1_000.0
        }
        activeDays += date
        estimateByDay[date] = (estimateByDay[date] ?: 0.0) + watchedSeconds
        val key = date.toString().take(7)
        if (key == monthKey) {
            estimateMonth += watchedSeconds
            if (completed) if (entry.isEpisode) episodes++ else movies++
        } else if (key == lastMonthKey) {
            estimateLastMonth += watchedSeconds
        }
    }

    val days = (WATCH_HISTORY_DAYS - 1 downTo 0).map { back ->
        val date = today.minus(back, DateTimeUnit.DAY)
        val counted = (secondsByDay[date.toString()] ?: 0L).toDouble()
        val seconds = maxOf(counted, estimateByDay[date] ?: 0.0)
        WatchHistoryDay(date, (seconds / 60.0 + 0.5).toInt())
    }
    val countedMonth = secondsByDay.filterKeys { it.startsWith(monthKey) }.values.sum().toDouble()
    val countedLastMonth = secondsByDay.filterKeys { it.startsWith(lastMonthKey) }.values.sum().toDouble()

    var record = 0
    var current = 0
    var previous: LocalDate? = null
    activeDays.sorted().forEach { day ->
        current = if (previous != null && previous!!.plus(1, DateTimeUnit.DAY) == day) current + 1 else 1
        previous = day
        if (current > record) record = current
    }
    val last = previous
    val streak = if (last != null && (last == today || last == today.minus(1, DateTimeUnit.DAY))) current else 0

    return WatchHistoryStats(
        days = days,
        minutesThisMonth = (maxOf(countedMonth, estimateMonth) / 60.0).toInt(),
        minutesLastMonth = (maxOf(countedLastMonth, estimateLastMonth) / 60.0).toInt(),
        streakDays = streak,
        recordDays = record,
        finishedMovies = movies,
        finishedEpisodes = episodes,
    )
}

/** O feed do Histórico: do mais novo para o mais velho. */
internal fun buildWatchHistoryFeed(entries: List<WatchProgressEntry>): List<WatchHistoryFeedEntry> =
    entries
        .filter { it.lastUpdatedEpochMs > 0L }
        .sortedByDescending { it.lastUpdatedEpochMs }
        .take(WATCH_HISTORY_FEED_LIMIT)
        .map { entry ->
            val time = localDateTimeOf(entry.lastUpdatedEpochMs)
            WatchHistoryFeedEntry(
                entry = entry,
                date = time.date,
                time = time,
                percent = if (entry.isEffectivelyCompleted) 100 else (entry.progressFraction * 100f + 0.5f).toInt(),
            )
        }
