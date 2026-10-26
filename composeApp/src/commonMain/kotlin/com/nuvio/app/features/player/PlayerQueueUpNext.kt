package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.glass.ambientLiquidGlass
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.player.skip.PlayerNextEpisodeRules
import com.nuvio.app.features.queue.QueueItem
import com.nuvio.app.features.queue.QueueRepository
import com.nuvio.app.features.queue.toMetaPreview
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Better Nuvio: "Da sua fila" (posplay.c na TV). No fim de um filme — ou do último
 * episódio disponível de uma série — o player oferece o próximo título da fila, no mesmo
 * ponto em que ofereceria o próximo episódio. Com o modo maratona, ele começa sozinho
 * depois de [PlayerNextEpisodeRules.QUEUE_BINGE_COUNTDOWN_SECONDS].
 */
@Composable
internal fun PlayerScreenRuntime.BindQueueUpNextEffects() {
    val queueState by remember {
        QueueRepository.ensureLoaded()
        QueueRepository.uiState
    }.collectAsStateWithLifecycle()

    // Título novo: a dispensa do anterior não vale mais (posplay_fechar).
    LaunchedEffect(parentMetaId, activeVideoId) {
        queueCardDismissed = false
        showQueueCard = false
        queueCountdown = null
    }

    LaunchedEffect(queueState, parentMetaId, isSeries, nextEpisodeInfo, playerMetaVideos.isEmpty()) {
        val endOfLine = !isLiveTvPlayback &&
            (!isSeries || (playerMetaVideos.isNotEmpty() && nextEpisodeInfo == null))
        queueNextItem = if (endOfLine) QueueRepository.nextAfter(parentMetaId) else null
    }

    LaunchedEffect(
        playbackSnapshot.positionMs,
        playbackSnapshot.durationMs,
        playbackSnapshot.isEnded,
        queueNextItem,
        queueCardDismissed,
    ) {
        val due = queueNextItem != null && !queueCardDismissed && isAtNextEpisodeThreshold()
        if (due != showQueueCard) showQueueCard = due
    }

    LaunchedEffect(showQueueCard, queueNextItem?.id, queueState.bingeMode) {
        queueCountdown = null
        val item = queueNextItem
        if (!showQueueCard || item == null || !queueState.bingeMode || isPartyControlLocked) return@LaunchedEffect
        for (remaining in PlayerNextEpisodeRules.QUEUE_BINGE_COUNTDOWN_SECONDS downTo 1) {
            queueCountdown = remaining
            delay(1_000L)
        }
        queueCountdown = null
        if (showQueueCard && queueNextItem?.id == item.id) playQueueItem(item, finishedCurrent = true)
    }
}

/**
 * Sai do player para o título da fila. Quando o atual terminou ([finishedCurrent]), ele
 * sai da fila — é o que já foi visto.
 */
internal fun PlayerScreenRuntime.playQueueItem(item: QueueItem, finishedCurrent: Boolean) {
    showQueueCard = false
    queueCountdown = null
    flushWatchProgress()
    if (finishedCurrent) QueueRepository.remove(parentMetaId)
    val play = args.onPlayQueueItem
    if (play != null) play(item) else args.onOpenMetaDetails?.invoke(item.toMetaPreview())
}

internal fun PlayerScreenRuntime.openQueuePanel() {
    showQueuePanel = true
    showCastPanel = false
    showSourcesPanel = false
    showQualityPanel = false
    showSpeedPanel = false
    showEpisodesPanel = false
    showChaptersPanel = false
    showLiveChannelsPanel = false
    controlsVisible = false
}

@Composable
internal fun QueueUpNextCard(
    item: QueueItem?,
    visible: Boolean,
    countdownSec: Int?,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (item == null) return
    PlatformBackHandler(enabled = visible, onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(animationSpec = tween(260), initialOffsetX = { it / 2 }) +
            fadeIn(animationSpec = tween(220)),
        exit = slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { it / 2 }) +
            fadeOut(animationSpec = tween(160)),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(16.dp)
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .ambientLiquidGlass(
                    shape = shape,
                    tint = Color(0xFF191919).copy(alpha = 0.64f),
                    fallback = Color(0xFF191919).copy(alpha = 0.89f),
                    blurRadius = 20.dp,
                    refractionHeight = 10.dp,
                    refractionAmount = 14.dp,
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                .clickable(onClick = onPlay)
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (countdownSec != null) {
                        stringResource(Res.string.bn_queue_starts_in, countdownSec)
                    } else {
                        stringResource(Res.string.bn_queue_from_your_queue)
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.action_close),
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = item.poster ?: item.banner,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 54.dp, height = 81.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.releaseInfo?.takeIf { it.isNotBlank() }?.let { info ->
                        Text(
                            text = info,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(Res.string.bn_queue_play),
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Better Nuvio: o painel "A seguir" da TV (aseguir.c), no menu Mais: a chave do modo
 * maratona, a fila na ordem com "Tocar", e "Limpar fila".
 */
@Composable
internal fun PlayerQueuePanel(
    visible: Boolean,
    onPlay: (QueueItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val queueState by remember {
        QueueRepository.ensureLoaded()
        QueueRepository.uiState
    }.collectAsStateWithLifecycle()

    PlayerSidePanel(
        visible = visible,
        onDismiss = onDismiss,
        width = 440.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            PlayerPanelHeader(title = stringResource(Res.string.bn_queue_up_next)) {
                PlayerDialogButton(
                    label = stringResource(Res.string.action_close),
                    onClick = onDismiss,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(tokens.shapes.compactCard)
                    .background(tokens.colors.surfacePopover)
                    .border(tokens.borders.thin, tokens.colors.borderSubtle, tokens.shapes.compactCard)
                    .clickable { QueueRepository.setBingeMode(!queueState.bingeMode) }
                    .padding(horizontal = NuvioTokens.Space.s14, vertical = NuvioTokens.Space.s10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.bn_queue_binge_title),
                        color = tokens.colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(Res.string.bn_queue_binge_sub),
                        color = tokens.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = queueState.bingeMode, onCheckedChange = QueueRepository::setBingeMode)
            }
            Spacer(Modifier.height(16.dp))
            if (queueState.items.isEmpty()) {
                Text(
                    text = stringResource(Res.string.bn_queue_empty_message),
                    color = tokens.colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                ) {
                    itemsIndexed(queueState.items, key = { _, item -> "${item.type}:${item.id}" }) { _, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(tokens.shapes.compactCard)
                                .background(tokens.colors.surfacePopover)
                                .clickable { onPlay(item) }
                                .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s10),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = item.poster ?: item.banner,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 40.dp, height = 60.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(tokens.colors.surfaceCard),
                            )
                            Spacer(Modifier.width(NuvioTokens.Space.s12))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    color = tokens.colors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                item.releaseInfo?.takeIf { it.isNotBlank() }?.let { info ->
                                    Text(
                                        text = info,
                                        color = tokens.colors.textMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Text(
                                text = stringResource(Res.string.bn_queue_play),
                                color = tokens.colors.textSecondary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                PlayerDialogButton(
                    label = stringResource(Res.string.bn_queue_clear),
                    onClick = QueueRepository::clear,
                )
            }
        }
    }
}
