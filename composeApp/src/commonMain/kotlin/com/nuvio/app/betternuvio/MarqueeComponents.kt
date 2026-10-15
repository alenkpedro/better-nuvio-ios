package com.nuvio.app.betternuvio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.toLibraryItem
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_view_details
import nuvio.composeapp.generated.resources.better_nuvio_hero_play
import nuvio.composeapp.generated.resources.hero_add_to_library
import nuvio.composeapp.generated.resources.hero_remove_from_library
import org.jetbrains.compose.resources.stringResource

/**
 * Better Nuvio hero building blocks: the Play pill (ink on the page, like the original
 * "View details" pill) and the translucent circles that sit beside it over artwork.
 */
object MarqueeDefaults {
    val ButtonHeight: Dp = 52.dp
    /** Translucent warm glass for circles over artwork. */
    val GlassFill = Color(0xFF1C1C1E).copy(alpha = 0.62f)
    val GlassBorder = Color.White.copy(alpha = 0.12f)
}

@Composable
fun MarqueePlayButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.nuvio.colors
    Row(
        modifier = modifier
            .height(MarqueeDefaults.ButtonHeight)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) colors.textPrimary else colors.surfaceCard)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 20.dp, end = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val content = if (enabled) colors.background else colors.textMuted
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun MarqueeCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = MarqueeDefaults.ButtonHeight,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MarqueeDefaults.GlassFill)
            .border(0.5.dp, MarqueeDefaults.GlassBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.nuvio.colors.textPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Hero actions on the phone Home: Play (movie) or Episodes (series), plus library. */
@Composable
fun BetterHeroActions(
    item: MetaPreview,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val libraryState by LibraryRepository.uiState.collectAsStateWithLifecycle()
    val isSaved = libraryState.let { LibraryRepository.isSaved(item.id, item.type) }
    val isMovie = item.type.equals("movie", ignoreCase = true)
    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarqueePlayButton(
            label = stringResource(if (isMovie) Res.string.better_nuvio_hero_play else Res.string.home_view_details),
            onClick = onPlay,
        )
        MarqueeCircleButton(
            icon = if (isSaved) Icons.Rounded.Check else Icons.Rounded.Add,
            contentDescription = stringResource(
                if (isSaved) Res.string.hero_remove_from_library else Res.string.hero_add_to_library,
            ),
            onClick = {
                scope.launch {
                    runCatching { LibraryRepository.toggleSaved(item.toLibraryItem(savedAtEpochMs = 0L)) }
                }
            },
        )
    }
    BetterHeroSourceLine(item = item, modifier = Modifier.padding(top = 10.dp))
    }
}

/**
 * Header background opacity for [com.nuvio.app.core.ui.NuvioScreenHeader] while a
 * [MarqueeScreenBackdrop] is showing: transparent at the top so the backdrop reads
 * through the large title, opaque once content scrolls under the sticky header.
 */
val LocalMarqueeHeaderOpacity = androidx.compose.runtime.staticCompositionLocalOf<(() -> Float)?> { null }

/**
 * A faint wash of a title's colours behind a tab's large title (the last one saved or
 * watched), fading out as the list scrolls.
 */
@Composable
fun MarqueeScreenBackdrop(
    imageUrl: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val light = rememberRoomLight(imageUrl)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
            .graphicsLayer {
                val offset = if (listState.firstVisibleItemIndex == 0) {
                    listState.firstVisibleItemScrollOffset.toFloat()
                } else {
                    with(density) { 420.dp.toPx() }
                }
                translationY = -offset * 0.5f
                alpha = (1f - offset / with(density) { 420.dp.toPx() }).coerceIn(0f, 1f)
            }
            .roomLight(light, centerYFraction = 0.32f, intensity = { 0.55f }),
    )
}

fun marqueeHeaderOpacity(listState: androidx.compose.foundation.lazy.LazyListState, fadeDistancePx: Float): () -> Float = {
    if (listState.firstVisibleItemIndex > 0) 1f
    else (listState.firstVisibleItemScrollOffset / fadeDistancePx).coerceIn(0f, 1f)
}

/**
 * The source the hero's Play will reuse (movies only: a series needs its next episode
 * resolved first). Same cached link and wording as the detail page's source line.
 */
@Composable
fun BetterHeroSourceLine(item: MetaPreview, modifier: Modifier = Modifier) {
    if (!item.type.equals("movie", ignoreCase = true)) return
    val settings by com.nuvio.app.features.player.PlayerSettingsRepository.uiState.collectAsStateWithLifecycle()
    val text = androidx.compose.runtime.remember(item.id, settings.streamReuseLastLinkEnabled, settings.streamReuseLastLinkCacheHours) {
        if (!settings.streamReuseLastLinkEnabled) {
            null
        } else {
            com.nuvio.app.features.streams.StreamLinkCacheRepository.getValid(
                com.nuvio.app.features.streams.StreamLinkCacheRepository.contentKey(
                    type = item.type,
                    videoId = item.id,
                    parentMetaId = item.id,
                ),
                settings.streamReuseLastLinkCacheHours * 60L * 60L * 1000L,
            )?.let(com.nuvio.app.features.details.components.DetailSourcePreview::describe)
        }
    } ?: return
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.nuvio.colors.textSecondary,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}
