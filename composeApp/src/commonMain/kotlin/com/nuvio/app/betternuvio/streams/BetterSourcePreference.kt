package com.nuvio.app.betternuvio.streams

import com.nuvio.app.betternuvio.BetterNuvioStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.epochMs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * THE SOURCE YOU PICKED, per title and profile (the TV's fontepref.c). Not the URL —
 * that changes every episode — but what identifies "the same source" in the next one:
 * the addon's `bingeGroup` first; otherwise the same addon with the same AUDIO
 * languages, with the resolution as tiebreak. Only manual picks are saved; valid
 * for 180 days, 200 titles at most (the oldest leaves).
 */
@Serializable
data class RememberedSource(
    val titleId: String,
    val savedAtMs: Long,
    val addonId: String,
    val bingeGroup: String? = null,
    val audio: String = "",
    val height: Int = 0,
    val label: String = "",
)

object BetterSourcePreference {
    private const val KEY = "better_nuvio_source_memory"
    private const val MAX = 200
    private const val VALIDITY_MS = 180L * 24 * 3600 * 1000
    private val json = Json { ignoreUnknownKeys = true }

    private var loadedFor: String? = null
    private var entries: MutableList<RememberedSource> = mutableListOf()

    private fun load(): MutableList<RememberedSource> {
        val key = ProfileScopedKey.of(KEY)
        if (loadedFor != key) {
            loadedFor = key
            entries = BetterNuvioStorage.loadString(key)
                ?.let { runCatching { json.decodeFromString<List<RememberedSource>>(it) }.getOrNull() }
                ?.toMutableList() ?: mutableListOf()
        }
        return entries
    }

    private fun save() {
        BetterNuvioStorage.saveString(ProfileScopedKey.of(KEY), json.encodeToString(entries.toList()))
    }

    /** "tt123:1:2" -> "tt123"; non-IMDb ids keep their prefix ("tmdb:456:1:2" -> "tmdb:456"). */
    fun titleBase(id: String): String {
        val first = id.indexOf(':')
        if (first < 0 || id.startsWith("tt")) return if (first < 0) id else id.substring(0, first)
        val second = id.indexOf(':', first + 1)
        return if (second < 0) id else id.substring(0, second)
    }

    private fun audioSignature(stream: StreamItem): String =
        StreamInsightsParser.signature(
            StreamInsightsParser.stripSubtitles(
                "${stream.name.orEmpty()} ${stream.description ?: stream.title.orEmpty()} ${stream.behaviorHints.filename.orEmpty()}",
            ),
        ).joinToString("+")

    fun remember(titleId: String, stream: StreamItem) {
        if (titleId.isBlank()) return
        val base = titleBase(titleId)
        val list = load()
        list.removeAll { it.titleId == base }
        list += RememberedSource(
            titleId = base,
            savedAtMs = epochMs(),
            addonId = stream.addonId,
            bingeGroup = stream.behaviorHints.bingeGroup?.takeIf { it.isNotBlank() },
            audio = audioSignature(stream),
            height = StreamInsightsParser.of(stream).height,
            label = stream.name.orEmpty().lineSequence().firstOrNull().orEmpty(),
        )
        while (list.size > MAX) list.removeAt(0)
        save()
    }

    fun forTitle(titleId: String): RememberedSource? {
        if (titleId.isBlank()) return null
        val base = titleBase(titleId)
        val entry = load().lastOrNull { it.titleId == base } ?: return null
        return entry.takeIf { epochMs() - it.savedAtMs <= VALIDITY_MS }
    }

    /** Whether [stream] is "the same source" as the remembered one. */
    internal fun matches(entry: RememberedSource, stream: StreamItem): Boolean {
        val binge = stream.behaviorHints.bingeGroup?.takeIf { it.isNotBlank() }
        if (entry.bingeGroup != null && binge != null) return entry.bingeGroup == binge
        if (entry.addonId != stream.addonId) return false
        if (audioSignature(stream) != entry.audio) return false
        val height = StreamInsightsParser.of(stream).height
        return entry.height == 0 || height == 0 || entry.height == height
    }

    /** The best match in [streams]: the same height first, then the first that matches. */
    fun pick(entry: RememberedSource, streams: List<StreamItem>): StreamItem? {
        val candidates = streams.filter { matches(entry, it) }
        return candidates.firstOrNull { StreamInsightsParser.of(it).height == entry.height } ?: candidates.firstOrNull()
    }
}
