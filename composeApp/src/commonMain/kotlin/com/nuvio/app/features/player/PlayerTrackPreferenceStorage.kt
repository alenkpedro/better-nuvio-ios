package com.nuvio.app.features.player

data class PersistedPlayerTrackPreference(
    val subtitleType: String? = null,
    val subtitleLanguage: String? = null,
    val subtitleName: String? = null,
    val subtitleTrackId: String? = null,
    val addonSubtitleId: String? = null,
    val addonSubtitleUrl: String? = null,
    val addonSubtitleAddonName: String? = null,
    val audioLanguage: String? = null,
    val audioName: String? = null,
    val audioTrackId: String? = null,
    val subtitleIsForced: Boolean? = null,
    // Better Nuvio: quando a legenda foi escolhida (legpref.c na TV). A escolha vale
    // 180 dias; sem data (gravada antes disto) continua valendo.
    val subtitleSavedAtMs: Long? = null,
)

// Better Nuvio: a escolha de legenda lembrada vale 180 dias, como LEGPREF_VALIDADE_S na TV.
internal const val SUBTITLE_PREFERENCE_TTL_MS: Long = 180L * 24 * 3600 * 1000

internal fun PersistedPlayerTrackPreference.isSubtitleChoiceExpired(nowMs: Long): Boolean {
    val savedAt = subtitleSavedAtMs ?: return false
    return nowMs - savedAt > SUBTITLE_PREFERENCE_TTL_MS
}

/**
 * Better Nuvio: o id da SERIE, como idBase em legpref.c: "tt1234567:1:2" -> "tt1234567";
 * ids que nao sao do IMDb levam o primeiro segmento a mais ("tmdb:123:1:2" -> "tmdb:123").
 * A escolha de legenda vale para todos os episodios da serie.
 */
internal fun trackPreferenceBaseId(id: String): String {
    val trimmed = id.trim()
    val first = trimmed.indexOf(':')
    if (first < 0) return trimmed
    if (trimmed.startsWith("tt")) return trimmed.substring(0, first)
    val second = trimmed.indexOf(':', first + 1)
    return if (second < 0) trimmed else trimmed.substring(0, second)
}

object PersistedSubtitleSelectionType {
    const val INTERNAL = "INTERNAL"
    const val ADDON = "ADDON"
    const val DISABLED = "DISABLED"
}

internal expect object PlayerTrackPreferenceStorage {
    fun load(contentId: String): PersistedPlayerTrackPreference?
    fun save(contentId: String, preference: PersistedPlayerTrackPreference)
    fun loadSubtitleDelayMs(videoId: String): Int?
    fun saveSubtitleDelayMs(videoId: String, delayMs: Int)
}
