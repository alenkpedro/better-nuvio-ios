package com.nuvio.app.features.autosync

import com.nuvio.app.features.player.SubtitleSyncCue
import kotlin.math.roundToLong

/**
 * Rewrites a subtitle file with every timestamp t mapped to scale * t + offset, keeping the rest of
 * the file (text, styling) untouched. Used when a player can only shift subtitles (sub-delay) but
 * AutoSync found a frame-rate scale, or an offset beyond the player's delay range.
 */
internal object AutoSyncLinearRetimer {
    enum class Format(val extension: String) { SRT_OR_VTT("srt"), ASS("ass") }

    class Output(val text: String, val extension: String)

    private val arrowTimestamp = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})([.,])(\d{1,3})""")
    private val assDialogue = Regex(
        """^(\s*Dialogue:\s*[^,]*,)(\d+):(\d{2}):(\d{2})[.:](\d{1,2}),(\d+):(\d{2}):(\d{2})[.:](\d{1,2}),""",
    )

    fun retime(
        body: String,
        cues: List<SubtitleSyncCue>,
        scale: Double,
        offsetMs: Long,
    ): Output {
        fun map(ms: Long): Long = (ms * scale + offsetMs).roundToLong().coerceAtLeast(0L)
        val isAss = body.contains("[Events]", ignoreCase = true) && assDialogue.containsMatchIn(
            body.lineSequence().firstOrNull { it.trimStart().startsWith("Dialogue:") }.orEmpty(),
        )
        if (isAss) {
            val text = body.lineSequence().joinToString("\n") { line ->
                val m = assDialogue.find(line) ?: return@joinToString line
                val g = m.groupValues
                val start = assMs(g[2], g[3], g[4], g[5])
                val end = assMs(g[6], g[7], g[8], g[9])
                g[1] + formatAss(map(start)) + "," + formatAss(map(end)) + "," + line.substring(m.range.last + 1)
            }
            return Output(text, Format.ASS.extension)
        }
        if (body.contains("-->")) {
            val text = body.lineSequence().joinToString("\n") { line ->
                if (!line.contains("-->")) return@joinToString line
                arrowTimestamp.replace(line) { m ->
                    val g = m.groupValues
                    val hours = g[1].toLongOrNull() ?: 0L
                    val fraction = g[5].padEnd(3, '0').take(3).toLong()
                    val ms = ((hours * 60 + g[2].toLong()) * 60 + g[3].toLong()) * 1000 + fraction
                    formatArrow(map(ms), g[4])
                }
            }
            return Output(text, if (body.trimStart().startsWith("WEBVTT")) "vtt" else Format.SRT_OR_VTT.extension)
        }
        // Anything else (TTML...): plain SRT from the parsed cues.
        val text = buildString {
            cues.forEachIndexed { i, cue ->
                append(i + 1).append('\n')
                append(formatArrow(map(cue.startTimeMs), ",")).append(" --> ")
                append(formatArrow(map(cue.endTimeMs), ",")).append('\n')
                append(cue.text).append("\n\n")
            }
        }
        return Output(text, Format.SRT_OR_VTT.extension)
    }

    private fun assMs(h: String, m: String, s: String, cs: String): Long =
        ((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + cs.padEnd(2, '0').toLong() * 10

    private fun formatAss(ms: Long): String {
        val cs = (ms + 5) / 10
        val h = cs / 360_000
        val m = (cs / 6_000) % 60
        val s = (cs / 100) % 60
        return "$h:${two(m)}:${two(s)}.${two(cs % 100)}"
    }

    private fun formatArrow(ms: Long, separator: String): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1_000) % 60
        val f = ms % 1_000
        return "${two(h)}:${two(m)}:${two(s)}$separator${f.toString().padStart(3, '0')}"
    }

    private fun two(v: Long): String = v.toString().padStart(2, '0')
}
