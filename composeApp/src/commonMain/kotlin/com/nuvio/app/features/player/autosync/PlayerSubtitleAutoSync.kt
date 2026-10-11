package com.nuvio.app.features.player.autosync

import com.nuvio.app.betternuvio.BetterNuvioPrefs
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import com.nuvio.app.features.player.AddonSubtitle
import com.nuvio.app.features.player.PlayerScreenRuntime
import com.nuvio.app.features.player.PlayerSubtitleCueParser
import com.nuvio.app.features.player.SUBTITLE_DELAY_MAX_MS
import com.nuvio.app.features.player.SUBTITLE_DELAY_MIN_MS
import com.nuvio.app.features.player.SubtitleLanguageMatching
import com.nuvio.app.features.player.sanitizePlaybackHeaders
import com.nuvio.app.features.player.selectedAddonSubtitle
import com.nuvio.app.features.player.setSubtitleDelay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.better_nuvio_autosync_already
import nuvio.composeapp.generated.resources.better_nuvio_autosync_applied
import nuvio.composeapp.generated.resources.better_nuvio_autosync_network
import nuvio.composeapp.generated.resources.better_nuvio_autosync_no_index
import nuvio.composeapp.generated.resources.better_nuvio_autosync_no_match
import nuvio.composeapp.generated.resources.better_nuvio_autosync_not_mkv
import nuvio.composeapp.generated.resources.better_nuvio_autosync_other_fps
import nuvio.composeapp.generated.resources.better_nuvio_autosync_other_version
import nuvio.composeapp.generated.resources.better_nuvio_autosync_too_far
import org.jetbrains.compose.resources.getString
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * SUBTITLE AUTOSYNC, ported from the Better Nuvio TV app (legsync.c).
 *
 * The MKV index is fetched as soon as the player gets the URL ([prepareSubtitleAutoSync]),
 * in parallel with opening the source; when the addon subtitle loads the alignment is
 * just math and lands before the first lines. A few hundred KB, once per file.
 */
internal object SubtitleAutoSyncIndexCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var url: String? = null
    private var pending: Deferred<MkvIndexResult>? = null

    fun get(url: String, headers: Map<String, String>): Deferred<MkvIndexResult> {
        val current = pending
        if (this.url == url && current != null) return current
        this.url = url
        return scope.async { MkvSubtitleIndex.read(url, headers) }.also { pending = it }
    }
}

private fun PlayerScreenRuntime.autoSyncSourceUrl(): String? {
    if (!BetterNuvioPrefs.subtitleAutoSync) return null
    if (activeTorrentInfoHash != null) return null
    val url = activeSourceUrl.trim()
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    val path = url.substringBefore('?').lowercase()
    if (path.endsWith(".m3u8") || path.endsWith(".mpd")) return null
    return url
}

/** Starts downloading the MKV index of the current source (no-op when already cached). */
internal fun PlayerScreenRuntime.prepareSubtitleAutoSync() {
    val url = autoSyncSourceUrl() ?: return
    SubtitleAutoSyncIndexCache.get(url, sanitizePlaybackHeaders(activeSourceHeaders))
}

/** Lower = tried first. Text before image, same language first, full before forced. */
private fun priority(t: MkvSubtitleTimes, language: String?, largest: Int): Int {
    val name = t.track.name.lowercase()
    val forced = "forced" in name || "forçad" in name || t.count < largest * 35 / 100
    val sameLanguage = !language.isNullOrBlank() &&
        SubtitleLanguageMatching.matchesLanguageCode(t.track.language, language)
    return (if (t.track.isImage) 4 else 0) + (if (sameLanguage) 0 else 2) + (if (forced) 1 else 0)
}

private fun formatSeconds(seconds: Double): String {
    val tenths = (abs(seconds) * 10).roundToLong()
    val sign = if (seconds < 0) "−" else "+"
    return "$sign${tenths / 10},${tenths % 10} s"
}

/**
 * Runs AutoSync for the selected addon subtitle. Call from a LaunchedEffect keyed by the
 * subtitle and source: a change cancels the run, like the "ticket" of the TV version.
 */
internal suspend fun PlayerScreenRuntime.runSubtitleAutoSync() {
    val subtitle: AddonSubtitle = selectedAddonSubtitle ?: return
    val url = autoSyncSourceUrl() ?: return
    val headers = sanitizePlaybackHeaders(activeSourceHeaders)
    val delayAtStart = subtitleDelayMs
    try {
        val index = SubtitleAutoSyncIndexCache.get(url, headers)
        val body = runCatching { httpGetTextWithHeaders(subtitle.url, headers) }.getOrNull() ?: return
        val cues = PlayerSubtitleCueParser.parse(body, subtitle.url)
        if (cues.size < SubtitleAligner.MIN_USABLE) {
            println("[autosync] addon subtitle has ${cues.size} lines: too short to compare")
            return
        }
        val starts = DoubleArray(cues.size) { cues[it].startTimeMs / 1000.0 }
        val ends = DoubleArray(cues.size) { cues[it].endTimeMs / 1000.0 }
        val result = index.await()
        val usable = result.tracks.filter { it.count >= SubtitleAligner.MIN_USABLE }
        if (usable.isEmpty()) {
            val message = when (result.reason) {
                MkvIndexReason.NOT_MKV -> getString(Res.string.better_nuvio_autosync_not_mkv)
                MkvIndexReason.NETWORK -> getString(Res.string.better_nuvio_autosync_network, result.httpStatus)
                else -> getString(Res.string.better_nuvio_autosync_no_index)
            }
            println("[autosync] no reference: ${result.reason} (HTTP ${result.httpStatus})")
            NuvioToastController.show(message, 3500L)
            return
        }
        val largest = usable.maxOf { it.count }
        val ordered = usable.sortedWith(
            compareBy<MkvSubtitleTimes> { priority(it, subtitle.language, largest) }.thenByDescending { it.count },
        )
        var best = SubtitleAlignment()
        withContext(Dispatchers.Default) {
            for (track in ordered.take(3)) {
                // PGS/VobSub: the index mixes "appears" and "disappears"; the addon
                // goes in with starts AND ends.
                val r = if (track.track.isImage) {
                    SubtitleAligner.alignEvents(starts, ends, track.starts, null)
                } else {
                    SubtitleAligner.align(starts, track.starts)
                }
                println(
                    "[autosync] ref track ${track.track.number} (${track.track.language}, ${track.track.codec}, " +
                        "${track.count} points): scale ${r.scale} offset ${r.offsetSeconds} s, matched " +
                        "${r.matched}/${r.usable} (${(r.fraction * 100).roundToInt()}%, chance " +
                        "${(r.chance * 100).roundToInt()}%) ${if (r.reliable) "RELIABLE" else "rejected"}",
                )
                if (r.reliable) { best = r; break }
                if (r.fraction > best.fraction) best = r
            }
        }
        // The person moved the delay while we worked: their choice wins.
        if (subtitleDelayMs != delayAtStart || selectedAddonSubtitle?.id != subtitle.id) return
        if (!best.reliable) {
            val addonEnd = starts.maxOrNull() ?: 0.0
            val refEnd = ordered.first().starts.maxOrNull() ?: 0.0
            val otherVersion = addonEnd > 0 && refEnd > 0 && abs(addonEnd - refEnd) > maxOf(120.0, refEnd * 0.1)
            val message = if (otherVersion) {
                getString(Res.string.better_nuvio_autosync_other_version)
            } else {
                getString(Res.string.better_nuvio_autosync_no_match, "${(best.fraction * 100).roundToInt()}%")
            }
            NuvioToastController.show(message, 3500L)
            return
        }
        if (best.scale != 1.0) {
            NuvioToastController.show(getString(Res.string.better_nuvio_autosync_other_fps), 3500L)
            return
        }
        if (abs(best.offsetSeconds) < 0.15) {
            NuvioToastController.show(getString(Res.string.better_nuvio_autosync_already), 2500L)
            return
        }
        val delayMs = (best.offsetSeconds * 1000).roundToLong()
        if (delayMs < SUBTITLE_DELAY_MIN_MS || delayMs > SUBTITLE_DELAY_MAX_MS) {
            NuvioToastController.show(
                getString(Res.string.better_nuvio_autosync_too_far, formatSeconds(best.offsetSeconds)), 3500L,
            )
            return
        }
        setSubtitleDelay(delayMs.toInt())
        NuvioToastController.show(
            getString(Res.string.better_nuvio_autosync_applied, formatSeconds(best.offsetSeconds)), 3000L,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("[autosync] failed: ${e.message}")
    }
}
