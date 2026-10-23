package com.nuvio.app.features.autosync

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Offset (and frame-rate scale) between an add-on subtitle and an embedded reference timeline,
 * from cue START times only. Port of the TV's legsync_alinhar (Better Nuvio Release/src/legsync.c):
 *
 * 1. For each candidate scale, every (add-on cue, reference cue) pair within [MAX_OFFSET_S] votes
 *    for the offset that would line them up, in 0.1 s bins; a ±[TOLERANCE_S] window sums the votes.
 * 2. The peak is refined in 20 ms steps and the final offset is the median gap of matched cues.
 * 3. Confidence is measured, never assumed: matched fraction high and well above CHANCE (the same
 *    count at wrong offsets), a clearly smaller second peak, and all three thirds of the film
 *    matching at the SAME offset (constant delay vs fps drift vs a different cut).
 *
 * Times compared, never text: the reference may be in another language. Pure, no state.
 *
 * Mapping: video time = scale * subtitle file time + offset.
 */
internal object AutoSyncCueStartAligner {
    private const val MAX_OFFSET_S = 600.0
    private const val BIN_S = 0.1
    private const val TOLERANCE_S = 0.40
    const val MIN_USABLE = 40
    private const val MIN_MATCHED = 25

    private val SCALES = doubleArrayOf(
        1.0, 25.0 / 23.976, 23.976 / 25.0, 24.0 / 23.976, 23.976 / 24.0, 25.0 / 24.0, 24.0 / 25.0,
    )

    // Deliberately wrong offsets used to measure how much a dense reference matches by chance.
    private val FAR_OFFSETS = doubleArrayOf(11.3, -13.7, 17.9, -23.1, 29.3, -37.9, 43.7, -51.1)

    data class Result(
        val scale: Double,
        val offsetSeconds: Double,
        val matched: Int,
        val usable: Int,
        val fraction: Double,
        val chance: Double,
        val worstThird: Double,
        val thirds: List<Double>,
        val confident: Boolean,
    ) {
        val offsetMs: Int get() = (offsetSeconds * 1000.0).roundToInt()
        val isUnitScale: Boolean get() = scale == 1.0

        fun describe(): String =
            "scale=${scale.fmt(5)} offset=${offsetSeconds.fmt(3)}s matched=$matched/$usable " +
                "(${(fraction * 100).roundToInt()}%, chance ${(chance * 100).roundToInt()}%, thirds " +
                thirds.joinToString("/") { (it * 100).roundToInt().toString() } + "%) " +
                if (confident) "CONFIDENT" else "rejected"
    }

    private class Candidate(
        val scale: Double,
        val offset: Double,
        val fraction: Double,
        val chance: Double,
        val worstThird: Double,
        val secondPeak: Double,
        val thirds: DoubleArray,
        val matched: Int,
        val usable: Int,
    )

    /** Aligns add-on cue starts (seconds, any order) against reference cue starts. */
    fun align(addonStarts: DoubleArray, referenceStarts: DoubleArray): Result {
        val empty = Result(1.0, 0.0, 0, 0, 0.0, 0.0, 0.0, listOf(0.0, 0.0, 0.0), false)
        if (addonStarts.size < MIN_USABLE || referenceStarts.size < MIN_USABLE) return empty
        val a = normalize(addonStarts)
        val r = normalize(referenceStarts)
        if (a.size < MIN_USABLE || r.size < MIN_USABLE) return empty

        val bins = (2 * MAX_OFFSET_S / BIN_S).toInt() + 2
        val hist = IntArray(bins)
        val diff = DoubleArray(a.size)
        val candidates = SCALES.map { evaluate(a, r, it, hist, diff) }

        // Best passing candidate. Scale 1 wins when within 3 points of the best:
        // changing the scale without need stretches the whole subtitle.
        var chosen = -1
        for (i in candidates.indices) {
            if (passes(candidates[i]) && (chosen < 0 || candidates[i].fraction > candidates[chosen].fraction)) {
                chosen = i
            }
        }
        if (chosen > 0 && passes(candidates[0]) && candidates[0].fraction >= candidates[chosen].fraction - 0.03) {
            chosen = 0
        }
        var shown = if (chosen >= 0) chosen else 0
        if (chosen < 0) {
            for (i in 1 until candidates.size) if (candidates[i].fraction > candidates[shown].fraction) shown = i
        }
        val c = candidates[shown]
        return Result(
            scale = c.scale,
            offsetSeconds = c.offset,
            matched = c.matched,
            usable = c.usable,
            fraction = c.fraction,
            chance = c.chance,
            worstThird = c.worstThird,
            thirds = c.thirds.toList(),
            confident = chosen >= 0,
        )
    }

    /**
     * Starts AND ends (legsync_alinhar_eventos). For an image reference (PGS/VobSub) the index
     * holds one point when a caption appears and another when it clears, so the add-on's cue ends
     * turn that second point from noise into signal. [referenceEnds] is optional.
     */
    fun alignEvents(
        addonStarts: DoubleArray,
        addonEnds: DoubleArray?,
        referenceStarts: DoubleArray,
        referenceEnds: DoubleArray? = null,
    ): Result {
        fun merge(starts: DoubleArray, ends: DoubleArray?): DoubleArray {
            val out = ArrayList<Double>(starts.size * 2)
            for (i in starts.indices) {
                out += starts[i]
                if (ends != null && i < ends.size && ends[i] > starts[i]) out += ends[i]
            }
            return out.toDoubleArray()
        }
        return align(merge(addonStarts, addonEnds), merge(referenceStarts, referenceEnds))
    }

    /** Sorts and merges starts closer than 50 ms (several lines of one ASS cue). */
    private fun normalize(values: DoubleArray): DoubleArray {
        val sorted = values.filter { it.isFinite() && it >= 0.0 }.sorted()
        val out = ArrayList<Double>(sorted.size)
        for (v in sorted) if (out.isEmpty() || v - out.last() >= 0.05) out += v
        return out.toDoubleArray()
    }

    private class Count(val matched: Int, val usable: Int)

    /** Add-on cues in [from, to) with a reference cue within [tol] at time = s*a + d. */
    private fun count(
        a: DoubleArray, from: Int, to: Int, r: DoubleArray,
        s: Double, d: Double, tol: Double, diff: DoubleArray?,
    ): Count {
        var j = 0
        var c = 0
        var u = 0
        val lo = r[0] - 10
        val hi = r[r.size - 1] + 10
        for (i in from until to) {
            val x = s * a[i] + d
            if (x < lo || x > hi) continue
            u++
            while (j + 1 < r.size && r[j + 1] <= x) j++
            var m = r[j] - x
            if (j + 1 < r.size && abs(r[j + 1] - x) < abs(m)) m = r[j + 1] - x
            if (abs(m) <= tol) {
                if (diff != null) diff[c] = m
                c++
            }
        }
        return Count(c, u)
    }

    private fun evaluate(a: DoubleArray, r: DoubleArray, s: Double, hist: IntArray, diff: DoubleArray): Candidate {
        val nb = hist.size
        val k = (TOLERANCE_S / BIN_S).roundToInt()
        hist.fill(0)
        var j = 0
        for (i in a.indices) {
            val x = s * a[i]
            while (j < r.size && r[j] < x - MAX_OFFSET_S) j++
            var b = j
            while (b < r.size && r[b] <= x + MAX_OFFSET_S) {
                hist[floor((r[b] - x + MAX_OFFSET_S) / BIN_S + 0.5).toInt()]++
                b++
            }
        }
        // Sliding ±k window sum via prefix sums.
        for (b in 1 until nb) hist[b] += hist[b - 1]
        fun window(b: Int): Int =
            hist[if (b + k < nb) b + k else nb - 1] - (if (b - k - 1 >= 0) hist[b - k - 1] else 0)
        var best = 0
        var bestBin = 0
        for (b in 0 until nb) {
            val v = window(b)
            if (v > best) { best = v; bestBin = b }
        }
        var second = 0
        for (b in 0 until nb) {
            if (abs(b - bestBin) <= 20) continue
            val v = window(b)
            if (v > second) second = v
        }
        val center = bestBin * BIN_S - MAX_OFFSET_S
        var bestOffset = 0.0
        var bestCount = -1
        var step = 0
        while (true) {
            val d = center - 0.3 + step * 0.02
            if (d > center + 0.3 + 1e-9) break
            val n = count(a, 0, a.size, r, s, d, TOLERANCE_S, null).matched
            if (n > bestCount) { bestCount = n; bestOffset = d }
            step++
        }
        val first = count(a, 0, a.size, r, s, bestOffset, TOLERANCE_S, diff)
        if (first.matched > 0) {
            val gaps = diff.copyOf(first.matched).sorted()
            bestOffset += gaps[first.matched / 2]
        }
        val final = count(a, 0, a.size, r, s, bestOffset, TOLERANCE_S, null)
        val fraction = if (final.usable > 0) final.matched.toDouble() / final.usable else 0.0
        var chanceSum = 0.0
        for (far in FAR_OFFSETS) {
            val c = count(a, 0, a.size, r, s, bestOffset + far, TOLERANCE_S, null)
            chanceSum += if (c.usable > 0) c.matched.toDouble() / c.usable else 0.0
        }
        val thirds = DoubleArray(3)
        var worst = 1.0
        for (t in 0 until 3) {
            val c = count(a, a.size * t / 3, a.size * (t + 1) / 3, r, s, bestOffset, TOLERANCE_S, null)
            val f = if (c.usable >= 8) c.matched.toDouble() / c.usable else 0.0
            thirds[t] = f
            if (f < worst) worst = f
        }
        return Candidate(
            scale = s,
            offset = bestOffset,
            fraction = fraction,
            chance = chanceSum / FAR_OFFSETS.size,
            worstThird = worst,
            secondPeak = if (best > 0) second.toDouble() / best else 1.0,
            thirds = thirds,
            matched = final.matched,
            usable = final.usable,
        )
    }

    private fun passes(c: Candidate): Boolean {
        val minThird = maxOf(c.chance + 0.12, 0.25)
        return c.usable >= MIN_USABLE && c.matched >= MIN_MATCHED &&
            c.fraction >= 0.40 && c.fraction - c.chance >= 0.25 && c.secondPeak <= 0.70 &&
            c.worstThird >= minThird
    }
}

private fun Double.fmt(decimals: Int): String {
    var factor = 1.0
    repeat(decimals) { factor *= 10.0 }
    val rounded = kotlin.math.round(this * factor) / factor
    return rounded.toString()
}
