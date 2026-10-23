package com.nuvio.app.features.player

import kotlin.math.abs

/*
 * Port of the TV build's subtitle_match.h and evidenciaLegenda (faixas.c).
 * A subtitle "matches" the video when both names reduce to the same release
 * (same words, ignoring the extension and language/forced suffixes) and,
 * when both frame rates are known, they differ by at most 0.08 fps.
 */

private val SubtitleMatchSuffixes = setOf(
    "pt", "br", "ptbr", "pob", "por", "portuguese", "eng", "en", "english",
    "es", "spa", "esp", "spanish", "forced", "sdh", "hi", "cc", "sub", "subs",
)

private val SubtitleMatchExtensions = setOf("mkv", "mp4", "avi", "srt", "ass", "ssa", "vtt", "sub")

private fun Char.isAsciiAlnum(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

internal fun subtitleMatchRelease(name: String?): String? {
    if (name.isNullOrEmpty()) return null
    val base = name.substringAfterLast('/')
    val cut = base.indexOfFirst { it == '?' || it == '#' }.let { if (it < 0) base else base.substring(0, it) }
    val words = ArrayList<String>()
    val current = StringBuilder()
    for (ch in cut) {
        if (ch.isAsciiAlnum()) {
            current.append(ch.lowercaseChar())
        } else if (current.isNotEmpty()) {
            words += current.toString()
            current.clear()
        }
        if (words.size >= 48) break
    }
    if (current.isNotEmpty() && words.size < 48) words += current.toString()
    if (words.isNotEmpty() && words.last() in SubtitleMatchExtensions) words.removeAt(words.lastIndex)
    while (words.isNotEmpty() && words.last() in SubtitleMatchSuffixes) words.removeAt(words.lastIndex)
    if (words.size < 2) return null
    val joined = words.joinToString("") { it.take(47) }
    return joined.takeIf { it.length >= 8 }
}

private val FpsInNameRegex = Regex("(?<![0-9])([0-9]+(?:\\.[0-9]*)?)fps", RegexOption.IGNORE_CASE)

internal fun subtitleMatchFpsFromName(name: String?): Double {
    if (name.isNullOrEmpty()) return 0.0
    for (match in FpsInNameRegex.findAll(name)) {
        val fps = match.groupValues[1].toDoubleOrNull() ?: continue
        if (fps in 15.0..120.0) return fps
    }
    return 0.0
}

internal fun subtitleReleaseMatch(
    videoName: String?,
    subtitleName: String?,
    videoFps: Double,
    subtitleFps: Double,
): Boolean {
    val video = subtitleMatchRelease(videoName) ?: return false
    val subtitle = subtitleMatchRelease(subtitleName) ?: return false
    if (video != subtitle) return false
    val vFps = if (videoFps > 0) videoFps else subtitleMatchFpsFromName(videoName)
    val sFps = if (subtitleFps > 0) subtitleFps else subtitleMatchFpsFromName(subtitleName)
    if (vFps > 0 && sFps > 0 && abs(vFps - sFps) > 0.08) return false
    return true
}

/** The video name used for matching: the stream file name, or a title/url that looks like a file. */
internal fun subtitleMatchVideoSource(
    fileName: String?,
    streamTitle: String?,
    sourceUrl: String?,
): String? {
    fun String?.looksLikeFile(): Boolean = this != null && (contains(".mkv") || contains(".mp4"))
    return fileName?.takeIf { it.isNotBlank() }
        ?: streamTitle?.takeIf { it.looksLikeFile() }
        ?: sourceUrl?.takeIf { it.looksLikeFile() }
}

/** Evidence the video carries for this subtitle (0 = none). Same weights as evidenciaLegenda on TV. */
internal data class SubtitleMatchEvidence(
    val score: Int,
    val sameFile: Boolean,
)

internal fun AddonSubtitle.matchEvidence(videoSource: String?, videoFps: Double?): SubtitleMatchEvidence? {
    if (videoSource.isNullOrBlank()) return null
    val vFps = videoFps?.takeIf { it > 0 } ?: 0.0
    val sFps = fps?.takeIf { it > 0 } ?: 0.0
    val fileMatch = subtitleReleaseMatch(videoSource, fileName, vFps, sFps)
    val releaseMatch = subtitleReleaseMatch(videoSource, releaseName, vFps, sFps)
    if (!fileMatch && !releaseMatch) return null
    val score = (if (fileMatch) 2 else 1) + (if (vFps > 0 && sFps > 0) 2 else 0)
    return SubtitleMatchEvidence(score = score, sameFile = fileMatch)
}

/** One recommended subtitle per group: the highest evidence wins, ties keep list order. */
internal fun List<AddonSubtitle>.recommendedSubtitle(videoSource: String?, videoFps: Double?): AddonSubtitle? {
    if (videoSource.isNullOrBlank()) return null
    var best: AddonSubtitle? = null
    var bestScore = 0
    for (subtitle in this) {
        val score = subtitle.matchEvidence(videoSource, videoFps)?.score ?: 0
        if (score > bestScore) {
            best = subtitle
            bestScore = score
        }
    }
    return best
}
