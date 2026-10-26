package com.nuvio.app.features.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.AppIconResource
import com.nuvio.app.core.ui.appIconPainter
import com.nuvio.app.core.ui.nuvioTypeScale
import com.nuvio.app.features.livetv.formatLiveTvEpgTime
import com.nuvio.app.features.livetv.rememberLiveTvEpgNowMillis
import com.nuvio.app.features.streams.StreamBadge
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Better Nuvio: um botão redondo da fileira de baixo; o nome aparece no toque longo. */
internal data class PlayerRoundAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val painter: Painter? = null,
    val iconSize: Dp = 20.dp,
    val popup: (@Composable () -> Unit)? = null,
)

/**
 * Better Nuvio: a parte de baixo do player como na TV. Logo (ou nome) com a linha
 * "T5 E5 · Episódio · Capítulo", os botões redondos sem texto à direita, e a barra com o
 * tempo decorrido de um lado e "−29:08 · termina às 21:43" do outro.
 */
@Composable
internal fun PlayerBottomBar(
    title: String,
    logoUrl: String?,
    infoLine: String?,
    onInfoLineClick: (() -> Unit)?,
    sourceLine: String?,
    showDetails: Boolean,
    actions: List<PlayerRoundAction>,
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    showRemainingTime: Boolean,
    onRuntimeClick: () -> Unit,
    metrics: PlayerLayoutMetrics,
    onTracksClick: (() -> Unit)?,
    onInteraction: () -> Unit,
    onSourceLineHeightChanged: (Int) -> Unit,
    timeline: @Composable (Modifier) -> Unit,
    badges: List<StreamBadge> = emptyList(),
) {
    val typeScale = MaterialTheme.nuvioTypeScale
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerTimelineContentInset),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    if (showDetails) {
                        PlayerTitleLogo(title = title, logoUrl = logoUrl, metrics = metrics)
                        if (!infoLine.isNullOrBlank()) {
                            Text(
                                text = infoLine,
                                style = typeScale.bodyMd.copy(fontSize = metrics.episodeInfoSize),
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .then(
                                        if (onInfoLineClick != null) {
                                            Modifier.clickable {
                                                onInteraction()
                                                onInfoLineClick()
                                            }
                                        } else Modifier,
                                    ),
                            )
                        }
                        if (badges.isNotEmpty()) {
                            PlayerBadges(badges = badges, modifier = Modifier.padding(top = 6.dp))
                        }
                        if (!sourceLine.isNullOrBlank() && !playbackSnapshot.isPlaying) {
                            androidx.compose.runtime.DisposableEffect(Unit) {
                                onDispose { onSourceLineHeightChanged(0) }
                            }
                            Text(
                                text = stringResource(Res.string.compose_player_via, sourceLine),
                                style = typeScale.labelSm.copy(fontSize = metrics.metadataSize),
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.onSizeChanged { onSourceLineHeightChanged(it.height) },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { action ->
                        PlayerRoundActionButton(action = action, onInteraction = onInteraction)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPlaybackTime(displayedPositionMs),
                    style = typeScale.bodyMd.copy(fontSize = metrics.timeSize, fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Box(Modifier.weight(1f)) {
                    timeline(Modifier)
                }
                PlayerRemainingLabel(
                    playbackSnapshot = playbackSnapshot,
                    displayedPositionMs = displayedPositionMs,
                    showRemainingTime = showRemainingTime,
                    metrics = metrics,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clickable(role = Role.Button) {
                            onRuntimeClick()
                            onInteraction()
                        },
                )
            }
            if (onTracksClick != null) {
                // Dica do gesto: arrastar para cima (ou tocar aqui) abre Áudio | Legendas | Sincronia.
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(50))
                        .clickable(role = Role.Button) {
                            onInteraction()
                            onTracksClick()
                        }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(Res.string.bn_player_tracks_hint),
                        style = typeScale.labelSm.copy(fontSize = 12.sp),
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTitleLogo(title: String, logoUrl: String?, metrics: PlayerLayoutMetrics) {
    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
    if (!logoUrl.isNullOrBlank() && !logoFailed) {
        AsyncImage(
            model = logoUrl,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { logoFailed = true },
            modifier = Modifier
                .height(34.dp)
                .widthIn(max = 220.dp),
        )
    } else {
        Text(
            text = title,
            style = MaterialTheme.nuvioTypeScale.titleLg.copy(
                fontSize = metrics.titleSize,
                lineHeight = metrics.titleSize * 1.16f,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayerRemainingLabel(
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    showRemainingTime: Boolean,
    metrics: PlayerLayoutMetrics,
    modifier: Modifier = Modifier,
) {
    val durationMs = playbackSnapshot.durationMs
    val nowMillis = rememberLiveTvEpgNowMillis()
    val remainingMs = (durationMs - displayedPositionMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    val text = if (durationMs <= 0L) {
        formatPlaybackTime(displayedPositionMs)
    } else {
        val speed = playbackSnapshot.playbackSpeed.takeIf { it > 0.01f } ?: 1f
        val endsAt = formatLiveTvEpgTime(nowMillis + (remainingMs / speed).toLong())
        val time = if (showRemainingTime) "−${formatPlaybackTime(remainingMs)}" else formatPlaybackTime(durationMs)
        "$time · ${stringResource(Res.string.bn_player_ends_at, endsAt)}"
    }
    Text(
        text = text,
        style = MaterialTheme.nuvioTypeScale.bodyMd.copy(fontSize = metrics.timeSize, fontWeight = FontWeight.Medium),
        color = Color.White.copy(alpha = 0.92f),
        maxLines = 1,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerRoundActionButton(action: PlayerRoundAction, onInteraction: () -> Unit) {
    var showLabel by remember { mutableStateOf(false) }
    LaunchedEffect(showLabel) {
        if (showLabel) {
            delay(1600)
            showLabel = false
        }
    }
    Box {
        Box(
            modifier = Modifier
                .size(38.dp)
                .playerLiquidGlass(
                    shape = CircleShape,
                    refractionHeight = 6.dp,
                    refractionAmount = 8.dp,
                )
                .clip(CircleShape)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = action.label,
                    onClick = {
                        onInteraction()
                        action.onClick()
                    },
                    onLongClick = {
                        onInteraction()
                        showLabel = true
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (action.painter != null) {
                Icon(action.painter, action.label, tint = Color.White, modifier = Modifier.size(action.iconSize))
            } else if (action.icon != null) {
                Icon(action.icon, action.label, tint = Color.White, modifier = Modifier.size(action.iconSize))
            }
        }
        if (showLabel) {
            val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
            val positionProvider = remember(gapPx) { AboveAnchorPositionProvider(gapPx) }
            Popup(popupPositionProvider = positionProvider, onDismissRequest = { showLabel = false }) {
                Text(
                    text = action.label,
                    style = MaterialTheme.nuvioTypeScale.labelSm.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF0A0A0B),
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.94f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        action.popup?.invoke()
    }
}

/** Episódios, Fontes, Capítulos, Qualidade, Velocidade, Ajustar tela, Próximo — o que houver. */
@Composable
internal fun playerRoundActions(
    playbackSnapshot: PlayerPlaybackSnapshot,
    resizeMode: PlayerResizeMode,
    onEpisodesClick: (() -> Unit)?,
    onSourcesClick: (() -> Unit)?,
    onChaptersClick: (() -> Unit)?,
    qualityLabel: String?,
    onQualityClick: (() -> Unit)?,
    onSpeedClick: (() -> Unit)?,
    speedPickerVisible: Boolean,
    onSpeedSelected: (Float) -> Unit,
    onSpeedPickerDismiss: () -> Unit,
    onResizeModeClick: () -> Unit,
    onNextEpisodeClick: (() -> Unit)?,
): List<PlayerRoundAction> = listOfNotNull(
    onEpisodesClick?.let {
        PlayerRoundAction(stringResource(Res.string.compose_player_episodes), it, painter = appIconPainter(AppIconResource.PlayerEpisodes))
    },
    onSourcesClick?.let {
        PlayerRoundAction(stringResource(Res.string.compose_player_sources), it, painter = appIconPainter(AppIconResource.PlayerSource))
    },
    onChaptersClick?.let {
        PlayerRoundAction(stringResource(Res.string.player_chapters), it, icon = Icons.AutoMirrored.Rounded.ListAlt)
    },
    onQualityClick?.let {
        PlayerRoundAction(qualityLabel?.takeIf { label -> label.isNotBlank() } ?: stringResource(Res.string.bn_player_quality), it, icon = Icons.Rounded.HighQuality)
    },
    onSpeedClick?.let {
        PlayerRoundAction(
            "${stringResource(Res.string.compose_player_speed)} ${formatPlaybackSpeedLabel(playbackSnapshot.playbackSpeed)}",
            it,
            icon = Icons.Rounded.Speed,
            popup = {
                PlayerSpeedPopup(
                    visible = speedPickerVisible,
                    currentSpeed = playbackSnapshot.playbackSpeed,
                    onSpeedSelected = onSpeedSelected,
                    onDismiss = onSpeedPickerDismiss,
                )
            },
        )
    },
    PlayerRoundAction(stringResource(resizeMode.labelRes), onResizeModeClick, painter = appIconPainter(AppIconResource.PlayerAspectRatio)),
    onNextEpisodeClick?.let {
        PlayerRoundAction(stringResource(Res.string.player_next_episode), it, icon = Icons.Rounded.SkipNext, iconSize = 24.dp)
    },
)

/** "T5 E5 · Circus · Capítulo 3" (ou o lançamento, nos filmes). */
@Composable
internal fun playerInfoLine(
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    releaseInfo: String?,
    liveProgram: String?,
    chapters: List<PlayerChapter>,
    positionMs: Long,
): String {
    val parts = mutableListOf<String>()
    if (seasonNumber != null && episodeNumber != null) {
        parts += stringResource(Res.string.compose_player_episode_code_full, seasonNumber, episodeNumber)
        com.nuvio.app.core.i18n.localizedEpisodeTitle(episodeTitle, episodeNumber)
            ?.takeIf { it.isNotBlank() }
            ?.let { parts += it }
    } else if (!liveProgram.isNullOrBlank()) {
        parts += liveProgram
    } else if (!releaseInfo.isNullOrBlank()) {
        parts += releaseInfo
    }
    val chapterIndex = chapters.currentChapterIndex(positionMs)
    if (chapterIndex >= 0) {
        val fallback = stringResource(Res.string.player_chapter_number, chapterIndex + 1)
        parts += chapters[chapterIndex].title.ifBlank { fallback }
    }
    return parts.joinToString(" · ")
}
