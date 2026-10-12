package com.nuvio.app.betternuvio.streams

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.streams.StreamItem
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.better_nuvio_stream_uncached
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong

/**
 * Better Nuvio rows under a stream, as on the TV source sheet (streams.c
 * stream_folha_desenhar): a meta line (container · resolution · Dolby Vision · Atmos ·
 * size), the AUDIO language chips and the detected badge art.
 *
 * [showBadgeArt] is false when the user already has imported (Fusion) badges for this
 * stream, so the two sets don't repeat each other. [showSize] is false when the official
 * size chip is already on screen.
 */
@Composable
internal fun BetterStreamDetails(
    stream: StreamItem,
    showBadgeArt: Boolean,
    showSize: Boolean,
    modifier: Modifier = Modifier,
) {
    val info = remember(stream) { StreamInsightsParser.of(stream) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val meta = buildList {
        info.container?.let(::add)
        if (info.height > 0) add("${info.height}p")
        if (info.dolbyVision) add("Dolby Vision")
        if (info.atmos) add("Atmos")
        if (showSize) info.sizeBytes?.let { add(formatSize(it)) }
    }
    val uncachedLabel = if (info.uncached) stringResource(Res.string.better_nuvio_stream_uncached) else null
    if (meta.isEmpty() && info.audioLanguages.isEmpty() && uncachedLabel == null &&
        (!showBadgeArt || info.badges.isEmpty())
    ) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (meta.isNotEmpty() || info.audioLanguages.isNotEmpty() || uncachedLabel != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = muted,
                    )
                }
                info.audioLanguages.forEach { Chip(it, filled = false) }
                uncachedLabel?.let { Chip("⏳ $it", filled = false, dim = true) }
            }
        }
        if (showBadgeArt && info.badges.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                info.badges.forEach { id ->
                    betterBadgeDrawable(id)?.let { res ->
                        Image(
                            painter = painterResource(res),
                            contentDescription = id,
                            modifier = Modifier.height(13.dp),
                            contentScale = ContentScale.FillHeight,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, filled: Boolean, dim: Boolean = false) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dim) 0.55f else 0.85f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (filled) 0.9f else 0.10f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp),
            color = color,
        )
    }
}

private fun formatSize(bytes: Long): String {
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gib >= 1.0) {
        val tenths = (gib * 10).roundToLong()
        return "${tenths / 10}.${tenths % 10} GB"
    }
    return "${(bytes / (1024.0 * 1024.0)).roundToLong()} MB"
}
