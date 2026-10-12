package com.nuvio.app.betternuvio.streams

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.betternuvio.BetterNuvioPrefs
import com.nuvio.app.features.streams.AddonStreamGroup
import com.nuvio.app.features.streams.StreamItem
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.better_nuvio_streams_by_resolution
import nuvio.composeapp.generated.resources.better_nuvio_streams_direct
import nuvio.composeapp.generated.resources.better_nuvio_streams_other
import nuvio.composeapp.generated.resources.better_nuvio_streams_responded
import nuvio.composeapp.generated.resources.better_nuvio_streams_torrent
import org.jetbrains.compose.resources.stringResource

/**
 * The TV source sheet's list rules (streams.c), applied over the official groups:
 * sections by RESOLUTION (4K / 1080p / 720p / SD / Other) with a count, filters
 * Type (direct / torrent) and Resolution, uncached-on-debrid sources at the end of
 * their section, and "N of M add-ons responded" while the search runs.
 */
object BetterStreamList {
    enum class Kind { ALL, DIRECT, TORRENT }

    var kind by mutableStateOf(Kind.ALL)
    var resolution by mutableStateOf<Int?>(null)

    /** The title the list is for, and the source remembered for it (set by StreamsScreen). */
    var titleId by mutableStateOf("")
        private set
    internal var remembered by mutableStateOf<RememberedSource?>(null)
        private set

    fun open(titleId: String) {
        if (this.titleId != titleId) {
            kind = Kind.ALL
            resolution = null
        }
        this.titleId = titleId
        remembered = BetterSourcePreference.forTitle(titleId)
    }

    /** A manual pick in the list: remember it for the next episode / resume. */
    fun onManualPick(stream: StreamItem) {
        if (titleId.isBlank()) return
        BetterSourcePreference.remember(titleId, stream)
        remembered = BetterSourcePreference.forTitle(titleId)
    }

    /**
     * Auto-play tries the remembered source first (TV: "a fonte lembrada e tentada
     * primeiro"), when the list is for the same title.
     */
    fun rememberedAutoPick(candidates: List<StreamItem>): StreamItem? =
        remembered?.let { BetterSourcePreference.pick(it, candidates) }

    fun isRemembered(stream: StreamItem): Boolean = remembered?.let { BetterSourcePreference.matches(it, stream) } == true

    private fun bucket(height: Int): Int = when {
        height >= 2160 -> 2160
        height >= 1080 -> 1080
        height >= 720 -> 720
        height > 0 -> 480
        else -> 0
    }

    private fun accepts(stream: StreamItem): Boolean {
        when (kind) {
            Kind.DIRECT -> if (stream.isTorrentStream) return false
            Kind.TORRENT -> if (!stream.isTorrentStream) return false
            Kind.ALL -> Unit
        }
        val r = resolution ?: return true
        return bucket(StreamInsightsParser.of(stream).height) == r
    }

    /** Uncached sources after the cached ones, order otherwise kept. */
    private fun cachedFirst(streams: List<StreamItem>): List<StreamItem> =
        streams.sortedBy { if (StreamInsightsParser.of(it).uncached) 1 else 0 }

    /**
     * The groups to draw. [labels] gives the section names for each resolution bucket
     * (2160, 1080, 720, 480, 0). Loading groups are kept so their "Fetching…" state shows.
     */
    fun arrange(groups: List<AddonStreamGroup>, labels: (Int) -> String): List<AddonStreamGroup> {
        val filtered = groups.map { g -> g.copy(streams = cachedFirst(g.streams.filter(::accepts))) }
        if (!BetterNuvioPrefs.streamsByResolution) return filtered
        val all = filtered.flatMap { it.streams }
        val byBucket = all.groupBy { bucket(StreamInsightsParser.of(it).height) }
        return listOf(2160, 1080, 720, 480, 0).mapNotNull { b ->
            val streams = byBucket[b].orEmpty()
            if (streams.isEmpty()) null
            else AddonStreamGroup(
                addonName = "${labels(b)} · ${streams.size}",
                addonId = "better-nuvio-res-$b",
                streams = cachedFirst(streams),
            )
        }
    }

    fun resolutionLabel(bucket: Int, other: String): String = when (bucket) {
        2160 -> "4K"
        1080 -> "1080p"
        720 -> "720p"
        480 -> "SD"
        else -> other
    }
}

/** Type / resolution filters and the grouping switch, under the official addon chips. */
@Composable
fun BetterStreamFilterBar(groups: List<AddonStreamGroup>, modifier: Modifier = Modifier) {
    val streams = groups.flatMap { it.streams }
    if (streams.isEmpty()) return
    val other = stringResource(Res.string.better_nuvio_streams_other)
    val buckets = streams.map {
        val h = StreamInsightsParser.of(it).height
        when {
            h >= 2160 -> 2160
            h >= 1080 -> 1080
            h >= 720 -> 720
            h > 0 -> 480
            else -> 0
        }
    }.toSet()
    val hasTorrent = streams.any { it.isTorrentStream }
    val hasDirect = streams.any { !it.isTorrentStream }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallChip(
            text = stringResource(Res.string.better_nuvio_streams_by_resolution),
            selected = BetterNuvioPrefs.streamsByResolution,
        ) { BetterNuvioPrefs.streamsByResolution = !BetterNuvioPrefs.streamsByResolution }
        Spacer(Modifier.width(4.dp))
        listOf(2160, 1080, 720, 480, 0).filter { it in buckets }.forEach { b ->
            SmallChip(
                text = BetterStreamList.resolutionLabel(b, other),
                selected = BetterStreamList.resolution == b,
            ) { BetterStreamList.resolution = if (BetterStreamList.resolution == b) null else b }
        }
        if (hasTorrent && hasDirect) {
            Spacer(Modifier.width(4.dp))
            SmallChip(
                text = stringResource(Res.string.better_nuvio_streams_direct),
                selected = BetterStreamList.kind == BetterStreamList.Kind.DIRECT,
            ) {
                BetterStreamList.kind = if (BetterStreamList.kind == BetterStreamList.Kind.DIRECT) {
                    BetterStreamList.Kind.ALL
                } else {
                    BetterStreamList.Kind.DIRECT
                }
            }
            SmallChip(
                text = stringResource(Res.string.better_nuvio_streams_torrent),
                selected = BetterStreamList.kind == BetterStreamList.Kind.TORRENT,
            ) {
                BetterStreamList.kind = if (BetterStreamList.kind == BetterStreamList.Kind.TORRENT) {
                    BetterStreamList.Kind.ALL
                } else {
                    BetterStreamList.Kind.TORRENT
                }
            }
        }
    }
}

@Composable
private fun SmallChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) scheme.onSurface else scheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = if (selected) scheme.surface else scheme.onSurface,
        )
    }
}

/** "N of M add-ons responded" with a thin bar, while sources are still arriving. */
@Composable
fun BetterStreamProgress(groups: List<AddonStreamGroup>, modifier: Modifier = Modifier) {
    val total = groups.size
    if (total == 0) return
    val done = groups.count { !it.isLoading }
    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.better_nuvio_streams_responded, done, total),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { done.toFloat() / total },
            modifier = Modifier.fillMaxWidth(0.5f).height(3.dp).clip(RoundedCornerShape(2.dp)),
        )
    }
}
