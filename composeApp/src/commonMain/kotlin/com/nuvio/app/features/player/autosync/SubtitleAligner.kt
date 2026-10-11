package com.nuvio.app.features.player.autosync

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Better Nuvio subtitle AutoSync: aligns an addon subtitle to a reference timeline
 * (the embedded track's MKV Cues). Port of legsync.c from the Better Nuvio TV app.
 *
 * 1. For each candidate scale, every (addon line, reference line) pair within
 *    [MAX_SHIFT_S] votes for the offset that would match them, in 0.1 s bins. A
 *    +-[TOLERANCE_S] window sums the votes: the peak is the offset where most lines
 *    start together.
 * 2. The peak is refined in 20 ms steps and the final offset is the median of the
 *    matched differences.
 * 3. Confidence is measured, never assumed:
 *    - matched fraction high, and well above CHANCE (the same count at wrong offsets:
 *      a dense reference matches a lot by luck);
 *    - the second peak, far from the first, clearly smaller (no ambiguity);
 *    - the three thirds of the video match with the SAME offset. That separates a
 *      constant delay from frame-rate drift (another scale wins) or a different cut.
 */
data class SubtitleAlignment(
    val scale: Double = 1.0,
    val offsetSeconds: Double = 0.0,
    val matched: Int = 0,
    val usable: Int = 0,
    val fraction: Double = 0.0,
    val chance: Double = 0.0,
    val worstThird: Double = 0.0,
    val thirds: List<Double> = listOf(0.0, 0.0, 0.0),
    val reliable: Boolean = false,
)

object SubtitleAligner {
    private const val MAX_SHIFT_S = 600.0
    private const val BIN_S = 0.1
    private const val TOLERANCE_S = 0.40
    const val MIN_USABLE = 40
    private const val MIN_MATCHED = 25

    private val SCALES = doubleArrayOf(
        1.0, 25.0 / 23.976, 23.976 / 25.0, 24.0 / 23.976, 23.976 / 24.0, 25.0 / 24.0, 24.0 / 25.0,
    )
    private val FAR = doubleArrayOf(11.3, -13.7, 17.9, -23.1, 29.3, -37.9, 43.7, -51.1)

    private class Candidate(
        val s: Double,
        val d: Double,
        val f: Double,
        val chance: Double,
        val third: Double,
        val second: Double,
        val thirds: DoubleArray,
        val matched: Int,
        val usable: Int,
    )

    /** Sorts and merges starts closer than 50 ms (several lines of one ASS event). */
    private fun normalize(v: DoubleArray): DoubleArray {
        val sorted = v.filter { it.isFinite() && it >= 0 }.sorted()
        val out = ArrayList<Double>(sorted.size)
        for (x in sorted) if (out.isEmpty() || x - out.last() >= 0.05) out += x
        return out.toDoubleArray()
    }

    private class Count(val matched: Int, val usable: Int)

    /** How many addon lines in [from, to) have a reference line within tol at time s*a + d. */
    private fun count(
        a: DoubleArray, from: Int, to: Int, r: DoubleArray,
        s: Double, d: Double, tol: Double, diffs: DoubleArray? = null,
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
                if (diffs != null) diffs[c] = m
                c++
            }
        }
        return Count(c, u)
    }

    private fun evaluate(a: DoubleArray, r: DoubleArray, s: Double, hist: IntArray, diffs: DoubleArray): Candidate {
        val nb = hist.size
        val k = (TOLERANCE_S / BIN_S).roundToInt()
        hist.fill(0)
        var j = 0
        for (x0 in a) {
            val x = s * x0
            while (j < r.size && r[j] < x - MAX_SHIFT_S) j++
            var b = j
            while (b < r.size && r[b] <= x + MAX_SHIFT_S) {
                hist[floor((r[b] - x + MAX_SHIFT_S) / BIN_S + 0.5).toInt()]++
                b++
            }
        }
        for (b in 1 until nb) hist[b] += hist[b - 1]
        fun window(b: Int): Int = hist[if (b + k < nb) b + k else nb - 1] - (if (b - k - 1 >= 0) hist[b - k - 1] else 0)
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
        var bestD = 0.0
        var bestC = -1
        var d = bestBin * BIN_S - MAX_SHIFT_S - 0.3
        while (d <= bestBin * BIN_S - MAX_SHIFT_S + 0.3) {
            val n = count(a, 0, a.size, r, s, d, TOLERANCE_S).matched
            if (n > bestC) { bestC = n; bestD = d }
            d += 0.02
        }
        val first = count(a, 0, a.size, r, s, bestD, TOLERANCE_S, diffs)
        if (first.matched > 0) {
            val sortedDiffs = diffs.copyOf(first.matched).also { it.sort() }
            bestD += sortedDiffs[first.matched / 2]
        }
        val final = count(a, 0, a.size, r, s, bestD, TOLERANCE_S)
        val f = if (final.usable > 0) final.matched.toDouble() / final.usable else 0.0
        var chanceSum = 0.0
        for (far in FAR) {
            val c = count(a, 0, a.size, r, s, bestD + far, TOLERANCE_S)
            chanceSum += if (c.usable > 0) c.matched.toDouble() / c.usable else 0.0
        }
        val thirds = DoubleArray(3)
        var worst = 1.0
        for (i in 0 until 3) {
            val c = count(a, a.size * i / 3, a.size * (i + 1) / 3, r, s, bestD, TOLERANCE_S)
            val tf = if (c.usable >= 8) c.matched.toDouble() / c.usable else 0.0
            thirds[i] = tf
            if (tf < worst) worst = tf
        }
        return Candidate(
            s = s, d = bestD, f = f, chance = chanceSum / FAR.size, third = worst,
            second = if (best > 0) second.toDouble() / best else 1.0,
            thirds = thirds, matched = final.matched, usable = final.usable,
        )
    }

    private fun passes(c: Candidate): Boolean {
        val minThird = maxOf(c.chance + 0.12, 0.25)
        return c.usable >= MIN_USABLE && c.matched >= MIN_MATCHED &&
            c.f >= 0.40 && c.f - c.chance >= 0.25 && c.second <= 0.70 && c.third >= minThird
    }

    /**
     * Aligns [addon] starts (seconds) to [reference] starts. The result maps an addon
     * time t to the video time scale * t + offsetSeconds.
     */
    fun align(addon: DoubleArray, reference: DoubleArray): SubtitleAlignment {
        if (addon.size < MIN_USABLE || reference.size < MIN_USABLE) return SubtitleAlignment()
        val a = normalize(addon)
        val r = normalize(reference)
        if (a.size < MIN_USABLE || r.size < MIN_USABLE) return SubtitleAlignment()
        val hist = IntArray((2 * MAX_SHIFT_S / BIN_S).toInt() + 2)
        val diffs = DoubleArray(a.size)
        val c = SCALES.map { evaluate(a, r, it, hist, diffs) }
        // The best one that passes. Scale 1 wins when within 3 points of the best:
        // switching scale without need stretches the whole subtitle.
        var chosen = -1
        for (i in c.indices) if (passes(c[i]) && (chosen < 0 || c[i].f > c[chosen].f)) chosen = i
        if (chosen > 0 && passes(c[0]) && c[0].f >= c[chosen].f - 0.03) chosen = 0
        var shown = if (chosen >= 0) chosen else 0
        if (chosen < 0) for (i in 1 until c.size) if (c[i].f > c[shown].f) shown = i
        val m = c[shown]
        return SubtitleAlignment(
            scale = m.s, offsetSeconds = m.d, matched = m.matched, usable = m.usable,
            fraction = m.f, chance = m.chance, worstThird = m.third,
            thirds = m.thirds.toList(), reliable = chosen >= 0,
        )
    }

    /**
     * STARTS AND ENDS. An image (PGS/VobSub) reference has one point when a line
     * appears and another when it disappears; in a dense sitcom those points cover so
     * much of the timeline that chance matches ~40% of the addon starts. Using the
     * addon ends too turns every "disappears" point into signal.
     */
    fun alignEvents(
        addonStarts: DoubleArray, addonEnds: DoubleArray?,
        refStarts: DoubleArray, refEnds: DoubleArray?,
    ): SubtitleAlignment {
        val a = ArrayList<Double>(addonStarts.size * 2)
        for (i in addonStarts.indices) {
            a += addonStarts[i]
            if (addonEnds != null && addonEnds[i] > addonStarts[i]) a += addonEnds[i]
        }
        val b = ArrayList<Double>(refStarts.size * 2)
        for (i in refStarts.indices) {
            b += refStarts[i]
            if (refEnds != null && refEnds[i] > refStarts[i]) b += refEnds[i]
        }
        return align(a.toDoubleArray(), b.toDoubleArray())
    }
}
