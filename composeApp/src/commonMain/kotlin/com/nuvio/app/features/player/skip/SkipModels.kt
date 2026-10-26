package com.nuvio.app.features.player.skip

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class SkipInterval(
    val startTime: Double,
    val endTime: Double,
    val type: String,
    val provider: String,
)

data class NextEpisodeInfo(
    val videoId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val thumbnail: String?,
    val overview: String?,
    val released: String?,
    val hasAired: Boolean,
    val isWatched: Boolean,
    val unairedMessage: String?,
)

enum class NextEpisodeThresholdMode {
    PERCENTAGE,
    MINUTES_BEFORE_END,
}

@Serializable
data class ArmEntry(
    @SerialName("myanimelist") val myanimelist: Int? = null,
    @SerialName("anilist") val anilist: Int? = null,
    @SerialName("kitsu") val kitsu: Int? = null,
    @SerialName("imdb") val imdb: String? = null,
)

// --- TheIntroDB API response models ---

// Better Nuvio: as tres chaves que a TV usa (intro.c). Cada uma e uma LISTA, porque um
// episodio pode ter mais de um trecho do mesmo tipo. "preview" (trecho do proximo
// episodio) fica de fora de proposito, como na TV.
@Serializable
data class TheIntroDbMediaResponse(
    @SerialName("type") val type: String? = null,
    @SerialName("intro") val intro: List<TheIntroDbSegment>? = null,
    @SerialName("recap") val recap: List<TheIntroDbSegment>? = null,
    @SerialName("credits") val credits: List<TheIntroDbSegment>? = null,
)

// MILISSEGUNDOS. `start_ms: null` = zero (comeca junto com a midia); `end_ms: null` = ate o fim.
@Serializable
data class TheIntroDbSegment(
    @SerialName("start_ms") val startMs: Long? = null,
    @SerialName("end_ms") val endMs: Long? = null,
)

/**
 * Better Nuvio: converte a resposta do TheIntroDB em trechos do player. Sem fim vira
 * Double.MAX_VALUE, o mesmo sentinela de "ate o fim" que os outros provedores usam.
 * Em filme os creditos sao "movie-credits"; vale o que comeca POR ULTIMO (#115 na TV:
 * um filme com creditos de abertura e finais marcados nao pode pular no comeco).
 */
internal fun TheIntroDbMediaResponse.toSkipIntervals(isMovie: Boolean): List<SkipInterval> {
    fun List<TheIntroDbSegment>?.convert(type: String): List<SkipInterval> =
        orEmpty().mapNotNull { segment ->
            val start = (segment.startMs ?: 0L).coerceAtLeast(0L) / 1000.0
            val end = segment.endMs?.let { it / 1000.0 } ?: Double.MAX_VALUE
            if (end <= start) null else SkipInterval(start, end, type, "theintrodb")
        }
    val credits = if (isMovie) {
        listOfNotNull(credits.convert("movie-credits").maxByOrNull { it.startTime })
    } else {
        credits.convert("outro").sortedByDescending { it.startTime }
    }
    return intro.convert("intro") + recap.convert("recap") + credits
}

@Serializable
data class SubmitIntroRequest(
    @SerialName("imdb_id") val imdbId: String,
    @SerialName("season") val season: Int,
    @SerialName("episode") val episode: Int,
    @SerialName("start_sec") val startSec: Double,
    @SerialName("end_sec") val endSec: Double,
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    @SerialName("segment_type") val segmentType: String,
)

// --- AniSkip API response models ---

@Serializable
data class AniSkipResponse(
    @SerialName("found") val found: Boolean = false,
    @SerialName("results") val results: List<AniSkipResult>? = null,
)

@Serializable
data class AniSkipResult(
    @SerialName("interval") val interval: AniSkipInterval,
    @SerialName("skipType") val skipType: String,
    @SerialName("skipId") val skipId: String? = null,
)

@Serializable
data class AniSkipInterval(
    @SerialName("startTime") val startTime: Double,
    @SerialName("endTime") val endTime: Double,
)

// --- Anime-Skip GraphQL API response models ---

@Serializable
data class AnimeSkipGraphqlResponse(
    @SerialName("data") val data: AnimeSkipData? = null,
)

@Serializable
data class AnimeSkipData(
    @SerialName("findShowsByExternalId") val findShowsByExternalId: List<AnimeSkipShow>? = null,
    @SerialName("findEpisodesByShowId") val findEpisodesByShowId: List<AnimeSkipEpisode>? = null,
)

@Serializable
data class AnimeSkipShow(
    @SerialName("id") val id: String,
)

@Serializable
data class AnimeSkipEpisode(
    @SerialName("season") val season: String? = null,
    @SerialName("number") val number: String? = null,
    @SerialName("timestamps") val timestamps: List<AnimeSkipTimestamp>? = null,
)

@Serializable
data class AnimeSkipTimestamp(
    @SerialName("at") val at: Double,
    @SerialName("type") val type: AnimeSkipTimestampType,
)

@Serializable
data class AnimeSkipTimestampType(
    @SerialName("name") val name: String,
)
