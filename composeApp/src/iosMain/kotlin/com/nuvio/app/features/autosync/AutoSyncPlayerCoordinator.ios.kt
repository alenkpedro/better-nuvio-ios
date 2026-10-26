package com.nuvio.app.features.autosync

import com.nuvio.app.core.logging.InAppLogger
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerSystemControlHandler
import com.nuvio.app.features.player.PlayerSystemControlSource
import com.nuvio.app.features.player.SUBTITLE_DELAY_MAX_MS
import com.nuvio.app.features.player.SUBTITLE_DELAY_MIN_MS
import com.nuvio.app.features.player.SubtitleSyncCue
import com.nuvio.app.features.player.languageMatchesPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val LOG = "AutoSync/iOS"

/**
 * AutoSync for the iOS (MPV) player, the TV's way (legsync.c / faixas.c):
 *
 * - the add-on subtitle is attached at once with its original timing, so nothing waits;
 * - the reference is the embedded subtitle timeline read from the Matroska Cues index
 *   ([MatroskaSubtitleCueIndex]); no index, no AutoSync (behaviour unchanged);
 * - the add-on cue starts are aligned against up to three embedded tracks (same language and
 *   text first, full before forced) with [AutoSyncCueStartAligner];
 * - only a confident fit is applied: as MPV sub-delay when the scale is 1, otherwise as a retimed
 *   copy of the file (scale baked in, offset still in sub-delay);
 * - for an automatic (startup) pick without a confident fit, the next add-on subtitle of the same
 *   language is tried, at most [MAX_ATTEMPTS] in all; if none fits the first one stays, untouched.
 *
 * A subtitle delay the user sets in this session wins: it cancels any run and turns AutoSync off
 * until the source changes.
 */
internal class AutoSyncIosCoordinator(
    private val scope: CoroutineScope,
    private val attachSubtitle: (url: String) -> Unit,
    private val applySubtitleDelayMs: (Int) -> Unit,
    private val rangeReaderFor: (url: String, headers: Map<String, String>) -> MatroskaSubtitleCueIndex.RangeReader?,
    private val downloadSubtitle: suspend (url: String, headers: Map<String, String>) -> String?,
    private val writeRetimedSubtitle: (name: String, text: String) -> String?,
) {
    private var sourceUrl = ""
    private var sourceHeaders: Map<String, String> = emptyMap()
    private var subtitleHeaders: (String) -> Map<String, String> = { emptyMap() }
    private var candidates: List<AutoSyncSubtitleCandidate> = emptyList()
    private var appliedListener: ((subtitleUrl: String, delayMs: Int) -> Unit)? = null
    private var job: Job? = null
    private var operationToken = 0L
    private var manualDelayThisSession = false
    private var reference: Deferred<MatroskaSubtitleCueIndex.Index>? = null
    private var referenceUrl = ""
    // Subtitles the current run attached or may attach: re-attaching one of them is not a change.
    private var runUrls: Set<String> = emptySet()

    fun setSource(url: String, headers: Map<String, String>, subtitleHeadersFor: (String) -> Map<String, String>) {
        subtitleHeaders = subtitleHeadersFor
        if (url == sourceUrl && headers == sourceHeaders) return
        val changed = url != sourceUrl
        sourceUrl = url
        sourceHeaders = headers
        if (changed) {
            cancel()
            manualDelayThisSession = false
            AutoSyncSyncedSubtitle.clear()
        }
        // Like the TV's legsync_preparar: fetch the index while the source opens, so the
        // alignment is pure arithmetic once a subtitle arrives.
        AutoSyncPreferencesRepository.ensureLoaded()
        if (AutoSyncPreferencesRepository.preferredSubtitleAutoSyncOnStart.value) referenceIndex()
    }

    fun setCandidates(value: List<AutoSyncSubtitleCandidate>) {
        candidates = value.distinctBy { it.url }
    }

    fun setAppliedListener(listener: ((subtitleUrl: String, delayMs: Int) -> Unit)?) {
        appliedListener = listener
    }

    fun cancel() {
        operationToken++
        job?.cancel()
        job = null
        runUrls = emptySet()
    }

    fun dispose() {
        cancel()
        reference?.cancel()
        reference = null
        appliedListener = null
    }

    fun onManualSubtitleDelay() {
        if (job != null) InAppLogger.info(LOG, "manual delay: AutoSync run cancelled")
        manualDelayThisSession = true
        cancel()
        AutoSyncSyncedSubtitle.clear()
    }

    /** Any subtitle change that is not an AutoSync start: the previous result no longer applies. */
    fun onSubtitleChangedWithoutAutoSync() {
        if (job != null) InAppLogger.info(LOG, "subtitle changed: AutoSync run cancelled")
        cancel()
        AutoSyncSyncedSubtitle.clear()
    }

    fun attachWithoutAutoSync(url: String) {
        // MPV lists the attached add-on subtitle as a track, and the runtime may pick the same
        // subtitle again while the run waits for the network: that must not cancel the run.
        if (job?.isActive == true && url in runUrls) {
            InAppLogger.info(LOG, "same subtitle attached again: AutoSync run kept")
            return
        }
        onSubtitleChangedWithoutAutoSync()
        attachSubtitle(url)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun referenceIndex(): Deferred<MatroskaSubtitleCueIndex.Index>? {
        val url = sourceUrl
        if (url.isBlank()) return null
        // A failed index (network, timeout) is asked again on the next subtitle, not kept for the session.
        reference?.takeIf {
            referenceUrl == url && !it.isCancelled &&
                !(it.isCompleted && it.getCompleted().outcome == MatroskaSubtitleCueIndex.Outcome.NETWORK)
        }?.let { return it }
        reference?.cancel()
        val reader = rangeReaderFor(url, sourceHeaders) ?: run {
            InAppLogger.info(LOG, "no range reader for this source (HLS or not http): AutoSync off")
            return null
        }
        referenceUrl = url
        return scope.async(Dispatchers.Default) {
            val index = try {
                MatroskaSubtitleCueIndex.load(reader)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                InAppLogger.warn(LOG, "embedded index failed: ${error.message}")
                MatroskaSubtitleCueIndex.Index(MatroskaSubtitleCueIndex.Outcome.NETWORK, emptyList())
            }
            InAppLogger.info(
                LOG,
                "embedded index ${index.outcome}: " + index.timelines.joinToString { t ->
                    "#${t.track.number} ${t.track.codecId} ${t.track.language.ifBlank { "?" }} ${t.size} cues"
                },
            )
            index
        }.also { reference = it }
    }

    fun start(url: String, candidateScope: AutoSyncCandidateScope) {
        cancel()
        AutoSyncSyncedSubtitle.clear()
        // Original timing on screen right away; AutoSync only ever adjusts it afterwards.
        attachSubtitle(url)
        AutoSyncPreferencesRepository.ensureLoaded()
        if (decideAutoSyncStart(AutoSyncPreferencesRepository.preferredSubtitleAutoSyncOnStart.value) ==
            AutoSyncStartAction.ATTACH_ORIGINAL
        ) {
            InAppLogger.info(LOG, "skipped: AutoSync is off in the settings")
            return
        }
        if (manualDelayThisSession) {
            InAppLogger.info(LOG, "skipped: the user set the subtitle delay in this session")
            return
        }
        val indexDeferred = referenceIndex() ?: return
        val token = operationToken
        val language = candidates.firstOrNull { it.url == url }?.language.orEmpty()
        val attempts = buildList {
            add(url)
            candidateScope.alternativeCandidates(candidates)
                .filter { it.url != url && language.isNotBlank() && languageMatchesPreference(it.language, language) }
                .forEach { add(it.url) }
        }.take(MAX_ATTEMPTS)
        runUrls = attempts.toSet()
        InAppLogger.info(LOG, "started with ${attempts.size} subtitle(s)")

        job = scope.launch {
            try {
                val index = indexDeferred.await()
                val references = orderReferences(index.timelines, language)
                if (references.isEmpty()) {
                    InAppLogger.info(LOG, "no embedded reference (${index.outcome}): subtitle left as is")
                    return@launch
                }
                for (attemptUrl in attempts) {
                    currentCoroutineContext().ensureActive()
                    if (token != operationToken) return@launch
                    val body = downloadSubtitle(attemptUrl, subtitleHeaders(attemptUrl)) ?: run {
                        InAppLogger.info(LOG, "could not download ${InAppLogger.redactUrl(attemptUrl)}")
                        null
                    } ?: continue
                    val fit = withContext(Dispatchers.Default) { alignAgainst(body, attemptUrl, references) }
                        ?: continue
                    if (token != operationToken) return@launch
                    apply(token, url, attemptUrl, fit)
                    return@launch
                }
                InAppLogger.info(LOG, "no confident fit in ${attempts.size} subtitle(s): original timing kept")
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                InAppLogger.warn(LOG, "AutoSync failed: ${error.message}")
            } finally {
                if (token == operationToken) {
                    job = null
                    runUrls = emptySet()
                }
            }
        }
    }

    private class Fit(
        val result: AutoSyncCueStartAligner.Result,
        val body: String,
        val cues: List<SubtitleSyncCue>,
    )

    private fun alignAgainst(
        body: String,
        url: String,
        references: List<MatroskaSubtitleCueIndex.Timeline>,
    ): Fit? {
        val cues = AutoSyncSubtitleCueParser.parse(body, url)
        if (cues.size < AutoSyncCueStartAligner.MIN_USABLE) {
            InAppLogger.info(LOG, "add-on subtitle has ${cues.size} cues: too short to compare")
            return null
        }
        val starts = DoubleArray(cues.size) { cues[it].startTimeMs / 1000.0 }
        val ends = DoubleArray(cues.size) { cues[it].endTimeMs / 1000.0 }
        for (reference in references.take(MAX_REFERENCES)) {
            // PGS/VobSub: the index mixes "appears" and "clears"; add the add-on cue ends too.
            val result = if (reference.track.isText) {
                AutoSyncCueStartAligner.align(starts, reference.starts)
            } else {
                AutoSyncCueStartAligner.alignEvents(starts, ends, reference.starts)
            }
            InAppLogger.info(
                LOG,
                "ref #${reference.track.number} (${reference.track.language.ifBlank { "?" }}, " +
                    "${reference.track.codecId}, ${reference.size} points): ${result.describe()}",
            )
            if (result.confident) return Fit(result, body, cues)
        }
        return null
    }

    private fun apply(token: Long, originalUrl: String, chosenUrl: String, fit: Fit) {
        val result = fit.result
        val tolerance = maxOf(ALREADY_IN_SYNC_MS, AutoSyncPreferencesRepository.syncToleranceMs.value)
        val delayFits = result.offsetMs in SUBTITLE_DELAY_MIN_MS..SUBTITLE_DELAY_MAX_MS
        if (chosenUrl != originalUrl) attachSubtitle(chosenUrl)
        val delayMs = if (result.isUnitScale && delayFits) {
            if (abs(result.offsetMs) <= tolerance) 0 else result.offsetMs
        } else {
            // Scale (or an offset beyond the delay range) baked into a retimed copy of the file.
            val bakedOffsetMs = if (delayFits) 0L else result.offsetMs.toLong()
            val retimed = AutoSyncLinearRetimer.retime(fit.body, fit.cues, result.scale, bakedOffsetMs)
            val path = writeRetimedSubtitle("autosync-${chosenUrl.hashCode().toUInt()}.${retimed.extension}", retimed.text)
            if (path == null) {
                InAppLogger.warn(LOG, "could not write the retimed subtitle: original timing kept")
                return
            }
            if (token != operationToken) return
            runUrls = runUrls + path
            attachSubtitle(path)
            if (delayFits) result.offsetMs else 0
        }
        if (token != operationToken) return
        InAppLogger.info(LOG, "applied scale=${result.scale} delay=${delayMs}ms to ${InAppLogger.redactUrl(chosenUrl)}")
        applySubtitleDelayMs(delayMs)
        appliedListener?.invoke(chosenUrl, delayMs)
        AutoSyncSyncedSubtitle.mark(chosenUrl)
    }

    /** Text before image, same language before others, full before forced, more cues first. */
    private fun orderReferences(
        timelines: List<MatroskaSubtitleCueIndex.Timeline>,
        language: String,
    ): List<MatroskaSubtitleCueIndex.Timeline> {
        val largest = timelines.maxOfOrNull { it.size } ?: 0
        fun forced(t: MatroskaSubtitleCueIndex.Timeline): Boolean {
            val name = t.track.name.lowercase()
            return name.contains("forced") || name.contains("forçad") || t.size < largest * 35 / 100
        }
        fun priority(t: MatroskaSubtitleCueIndex.Timeline): Int =
            (if (t.track.isText) 0 else 4) +
                (if (language.isNotBlank() && languageMatchesPreference(t.track.language, language)) 0 else 2) +
                (if (forced(t)) 1 else 0)
        return timelines
            .filter { it.size >= AutoSyncCueStartAligner.MIN_USABLE }
            .sortedWith(compareBy<MatroskaSubtitleCueIndex.Timeline> { priority(it) }.thenByDescending { it.size })
    }

    private companion object {
        const val MAX_REFERENCES = 3
        const val MAX_ATTEMPTS = 4
        const val ALREADY_IN_SYNC_MS = 150
    }
}

/**
 * Nuvio's MPV controller with AutoSync layered on top. Every call is forwarded to [base]; subtitle
 * changes first stop any AutoSync run that still targets the old subtitle.
 */
internal class AutoSyncIosPlayerEngineController(
    private val base: PlayerEngineController,
    private val coordinator: AutoSyncIosCoordinator,
) : PlayerEngineController by base, AutoSyncPlayerController, PlayerSystemControlSource {

    // The MPV controller is also the lock-screen / Now Playing source; keep that visible through the wrapper.
    private val systemControls = base as? PlayerSystemControlSource

    override val keepsPlaybackInBackground: Boolean
        get() = systemControls?.keepsPlaybackInBackground ?: false

    override fun setSystemControlHandler(handler: PlayerSystemControlHandler?) {
        systemControls?.setSystemControlHandler(handler)
    }

    override fun setAutoSyncSubtitleCandidates(candidates: List<AutoSyncSubtitleCandidate>) =
        coordinator.setCandidates(candidates)

    override fun setAutoSyncAppliedListener(listener: ((subtitleUrl: String, delayMs: Int) -> Unit)?) =
        coordinator.setAppliedListener(listener)

    override fun setSubtitleUri(url: String) = coordinator.attachWithoutAutoSync(url)

    override fun setSubtitleUriWithAutoSync(url: String) =
        coordinator.start(url, AutoSyncCandidateScope.STARTUP_SEARCH)

    override fun setSubtitleUriWithSelectedAutoSync(url: String) =
        coordinator.start(url, AutoSyncCandidateScope.SELECTED_ONLY)

    override fun selectSubtitleTrack(index: Int) {
        coordinator.onSubtitleChangedWithoutAutoSync()
        base.selectSubtitleTrack(index)
    }

    override fun clearExternalSubtitle() {
        coordinator.onSubtitleChangedWithoutAutoSync()
        base.clearExternalSubtitle()
    }

    override fun clearExternalSubtitleAndSelect(trackIndex: Int) {
        coordinator.onSubtitleChangedWithoutAutoSync()
        base.clearExternalSubtitleAndSelect(trackIndex)
    }

    override fun cancelForManualSubtitleDelay() = coordinator.onManualSubtitleDelay()
}
