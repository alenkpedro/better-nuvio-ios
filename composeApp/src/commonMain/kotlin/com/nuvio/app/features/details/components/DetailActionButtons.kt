package com.nuvio.app.features.details.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import com.nuvio.app.features.ratings.UserRatingStars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextAlign
import com.nuvio.app.core.ui.corViva
import com.nuvio.app.core.ui.onCorViva
import kotlin.math.roundToInt
import nuvio.composeapp.generated.resources.bn_action_download
import nuvio.composeapp.generated.resources.bn_action_external
import nuvio.composeapp.generated.resources.bn_action_from_start
import nuvio.composeapp.generated.resources.bn_action_my_list
import nuvio.composeapp.generated.resources.bn_action_shuffle
import nuvio.composeapp.generated.resources.bn_action_sources
import nuvio.composeapp.generated.resources.bn_action_watched
import nuvio.composeapp.generated.resources.bn_rating_disliked
import nuvio.composeapp.generated.resources.bn_rating_liked
import nuvio.composeapp.generated.resources.bn_rating_long_press_hint
import nuvio.composeapp.generated.resources.bn_rating_loved
import nuvio.composeapp.generated.resources.bn_resume_available
import com.nuvio.app.core.ui.AppIconResource
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.core.ui.appIconPainter
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_play
import nuvio.composeapp.generated.resources.details_download_action
import nuvio.composeapp.generated.resources.details_actions_menu_label
import nuvio.composeapp.generated.resources.hero_add_to_library
import nuvio.composeapp.generated.resources.hero_mark_unwatched
import nuvio.composeapp.generated.resources.hero_mark_watched
import nuvio.composeapp.generated.resources.hero_remove_from_library
import nuvio.composeapp.generated.resources.playback_unavailable
import nuvio.composeapp.generated.resources.random_episode_title
import nuvio.composeapp.generated.resources.shuffle_stop
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.Replay
import nuvio.composeapp.generated.resources.details_action_start_from_beginning
import nuvio.composeapp.generated.resources.sidebar_library
import nuvio.composeapp.generated.resources.streams_open_external_player
import org.jetbrains.compose.resources.stringResource
import com.nuvio.app.core.ui.glass.ambientLiquidGlass
import com.nuvio.app.core.ui.glass.isAmbientLiquidGlassActive

data class DetailSecondaryAction(
    val label: String,
    val icon: ImageVector,
    val drawable: DrawableResource? = null,
    val isActive: Boolean = false,
    val onClick: () -> Unit = {},
    val onLongClick: (() -> Unit)? = null,
    // Better Nuvio: short name shown under the round button ("Minha lista", "Visto").
    val shortLabel: String = label,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailActionButtons(
    modifier: Modifier = Modifier,
    playLabel: String = stringResource(Res.string.action_play),
    playEnabled: Boolean = true,
    pinnedAction: DetailSecondaryAction? = null,
    secondaryActions: List<DetailSecondaryAction> = emptyList(),
    actionsMenuLabel: String = stringResource(Res.string.details_actions_menu_label),
    isTablet: Boolean = false,
    onPlayClick: () -> Unit = {},
    onPlayLongClick: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null,
    iconActionRow: Boolean = false,
    iconActions: List<DetailSecondaryAction> = emptyList(),
    userRating: Int? = null,
    onRateClick: (() -> Unit)? = null,
    resumeProgress: Float? = null,
    onQuickRate: ((Int?) -> Unit)? = null,
) {
    val playPainter = appIconPainter(AppIconResource.PlayerPlay)
    val buttonHeight = if (isTablet) 56.dp else 52.dp
    val iconButtonSize = buttonHeight
    val playShape = RoundedCornerShape(40.dp)
    val hapticFeedback = LocalHapticFeedback.current
    var actionsExpanded by remember { mutableStateOf(false) }
    val menuProgress by animateFloatAsState(
        targetValue = if (actionsExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "detail_action_menu_progress",
    )
    val hasSecondaryActions = secondaryActions.isNotEmpty()

    Column(
        modifier = modifier
            .widthIn(max = if (isTablet) 520.dp else 420.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        resumeProgress?.let { ResumeAvailableLine(it) }
        if (iconActionRow) {
            PlayButton(
                playLabel = playLabel,
                playEnabled = playEnabled,
                playPainter = playPainter,
                playShape = playShape,
                buttonHeight = buttonHeight,
                isTablet = isTablet,
                onPlayClick = onPlayClick,
                onPlayLongClick = onPlayLongClick,
                modifier = Modifier.fillMaxWidth(),
            )
            if (iconActions.isNotEmpty()) {
                DetailIconActionRow(
                    actions = iconActions,
                    preferredSize = iconButtonSize,
                    preferredSpacing = if (isTablet) 20.dp else 16.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            onRateClick?.let { rate ->
                TitleRatingControl(
                    rating = userRating,
                    onRateClick = rate,
                    onQuickRate = onQuickRate,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            return@Column
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(buttonHeight),
        ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayButton(
                playLabel = playLabel,
                playEnabled = playEnabled,
                playPainter = playPainter,
                playShape = playShape,
                buttonHeight = buttonHeight,
                isTablet = isTablet,
                onPlayClick = onPlayClick,
                onPlayLongClick = onPlayLongClick,
                modifier = Modifier.weight(1f),
            )

            if (pinnedAction != null) {
                Spacer(modifier = Modifier.width(12.dp))
                DetailIconAction(
                    label = pinnedAction.label,
                    icon = pinnedAction.icon,
                    active = pinnedAction.isActive,
                    progress = 1f,
                    size = iconButtonSize,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        pinnedAction.onClick()
                    },
                )
            }

            if (hasSecondaryActions) {
                Spacer(modifier = Modifier.width(12.dp))
                secondaryActions.forEachIndexed { index, action ->
                    Box(
                        modifier = Modifier
                            .width(iconButtonSize * menuProgress)
                            .height(iconButtonSize)
                            .graphicsLayer {
                                clip = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (actionsExpanded || menuProgress > 0.01f) {
                            DetailIconAction(
                                label = action.label,
                                icon = action.icon,
                                drawable = action.drawable,
                                active = action.isActive,
                                progress = menuProgress,
                                size = iconButtonSize,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    action.onClick()
                                },
                                onLongClick = action.onLongClick?.let { longClick ->
                                    {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        longClick()
                                    }
                                },
                            )
                        }
                    }

                    if (index != secondaryActions.lastIndex) {
                        Spacer(modifier = Modifier.width(12.dp * menuProgress))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp * menuProgress))
            }

            if (hasSecondaryActions) {
                val moreGlassActive = !actionsExpanded && isAmbientLiquidGlassActive()
                Surface(
                    modifier = Modifier
                        .size(iconButtonSize)
                        .then(
                            if (moreGlassActive) {
                                Modifier.ambientLiquidGlass(
                                    shape = CircleShape,
                                    tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    fallback = Color.Transparent,
                                )
                            } else {
                                Modifier
                            },
                        ),
                    shape = CircleShape,
                    color = if (actionsExpanded) {
                        MaterialTheme.colorScheme.onBackground
                    } else if (moreGlassActive) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
                    },
                    contentColor = if (actionsExpanded) {
                        MaterialTheme.colorScheme.background
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .size(iconButtonSize)
                            .clickable(role = Role.Button) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                actionsExpanded = !actionsExpanded
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = actionsMenuLabel,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    rotationZ = 90f * menuProgress
                                },
                        )
                    }
                }
            }
        }
    }

        if (onDownloadClick != null) {
            val downloadGlassActive = isAmbientLiquidGlassActive()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight)
                    .then(
                        if (downloadGlassActive) {
                            Modifier.ambientLiquidGlass(
                                shape = playShape,
                                tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                fallback = Color.Transparent,
                            )
                        } else {
                            Modifier
                        },
                    ),
                shape = playShape,
                color = if (downloadGlassActive) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onDownloadClick)
                        .height(buttonHeight),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(if (isTablet) 20.dp else 18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.details_download_action),
                        style = if (isTablet) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        onRateClick?.let { rate ->
            TitleRatingControl(
                rating = userRating,
                onRateClick = rate,
                onQuickRate = onQuickRate,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

// Upstream's single entry point for the detail actions (phone sections and the tablet hero).
// Fork additions: download / play from start / external player in the icon row, the icon-row
// layout itself, the user rating stars, and the fork's watched/library icons.
@Composable
fun DetailActions(
    playLabel: String,
    playEnabled: Boolean,
    isSaved: Boolean,
    isWatched: Boolean,
    isTablet: Boolean,
    shuffleEnabled: Boolean,
    onPlayClick: () -> Unit,
    onPlayLongClick: (() -> Unit)?,
    onShuffleClick: (() -> Unit)?,
    onWatchedClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveLongClick: (() -> Unit)?,
    onDownloadClick: (() -> Unit)? = null,
    onPlayFromStartClick: (() -> Unit)? = null,
    onPlayExternallyClick: (() -> Unit)? = null,
    iconActionRow: Boolean = false,
    userRating: Int? = null,
    onRateClick: (() -> Unit)? = null,
    resumeProgress: Float? = null,
    onSourcesClick: (() -> Unit)? = null,
    onQuickRate: ((Int?) -> Unit)? = null,
) {
    val actionSet = detailActionSet(
        isSaved = isSaved,
        isWatched = isWatched,
        shuffleEnabled = shuffleEnabled,
        onShuffleClick = onShuffleClick,
        onWatchedClick = onWatchedClick,
        onSaveClick = onSaveClick,
        onSaveLongClick = onSaveLongClick,
        onDownloadClick = onDownloadClick,
        onPlayFromStartClick = onPlayFromStartClick,
        onPlayExternallyClick = onPlayExternallyClick,
        onSourcesClick = onSourcesClick,
    )
    val shuffleAction = actionSet.shuffle
    val watchedAction = actionSet.watched
    val saveAction = actionSet.save
    val iconActions = actionSet.icons
    DetailActionButtons(
        playLabel = if (playEnabled) playLabel else stringResource(Res.string.playback_unavailable),
        playEnabled = playEnabled,
        pinnedAction = shuffleAction?.takeIf { shuffleEnabled },
        iconActionRow = iconActionRow,
        iconActions = iconActions,
        secondaryActions = buildList {
            if (!shuffleEnabled) shuffleAction?.let(::add)
            add(watchedAction)
            add(saveAction)
        },
        isTablet = isTablet,
        onPlayClick = onPlayClick,
        onDownloadClick = onDownloadClick.takeIf { !iconActionRow },
        onPlayLongClick = onPlayLongClick,
        userRating = userRating,
        onRateClick = onRateClick,
        resumeProgress = resumeProgress,
        onQuickRate = onQuickRate,
    )
}

private class DetailActionSet(
    val shuffle: DetailSecondaryAction?,
    val watched: DetailSecondaryAction,
    val save: DetailSecondaryAction,
    val icons: List<DetailSecondaryAction>,
)

@Composable
private fun detailActionSet(
    isSaved: Boolean,
    isWatched: Boolean,
    shuffleEnabled: Boolean,
    onShuffleClick: (() -> Unit)?,
    onWatchedClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveLongClick: (() -> Unit)?,
    onDownloadClick: (() -> Unit)?,
    onPlayFromStartClick: (() -> Unit)?,
    onPlayExternallyClick: (() -> Unit)?,
    onSourcesClick: (() -> Unit)? = null,
): DetailActionSet {
    val shuffleAction = onShuffleClick?.let { onClick ->
        DetailSecondaryAction(
            label = stringResource(if (shuffleEnabled) Res.string.shuffle_stop else Res.string.random_episode_title),
            icon = Icons.Default.Shuffle,
            isActive = shuffleEnabled,
            onClick = onClick,
            shortLabel = stringResource(Res.string.bn_action_shuffle),
        )
    }
    val watchedAction = DetailSecondaryAction(
        label = stringResource(if (isWatched) Res.string.hero_mark_unwatched else Res.string.hero_mark_watched),
        icon = if (isWatched) Icons.Default.Visibility else Icons.Default.VisibilityOff,
        isActive = isWatched,
        onClick = onWatchedClick,
        shortLabel = stringResource(Res.string.bn_action_watched),
    )
    val saveAction = DetailSecondaryAction(
        label = stringResource(if (isSaved) Res.string.hero_remove_from_library else Res.string.hero_add_to_library),
        icon = Icons.Default.Add,
        drawable = Res.drawable.sidebar_library.takeIf { isSaved },
        isActive = isSaved,
        onClick = onSaveClick,
        onLongClick = onSaveLongClick,
        shortLabel = stringResource(Res.string.bn_action_my_list),
    )
    val downloadLabel = stringResource(Res.string.details_download_action)
    val playFromStartLabel = stringResource(Res.string.details_action_start_from_beginning)
    val externalLabel = stringResource(Res.string.streams_open_external_player)
    val sourcesLabel = stringResource(Res.string.bn_action_sources)
    val fromStartShort = stringResource(Res.string.bn_action_from_start)
    val downloadShort = stringResource(Res.string.bn_action_download)
    val externalShort = stringResource(Res.string.bn_action_external)
    // Better Nuvio: same order as the TV page (Do comeco, Minha lista, Fontes, Visto, Baixar).
    val iconActions = buildList {
        onPlayFromStartClick?.let { playFromStart ->
            add(
                DetailSecondaryAction(
                    label = playFromStartLabel,
                    icon = Icons.Rounded.Replay,
                    onClick = playFromStart,
                    shortLabel = fromStartShort,
                ),
            )
        }
        add(saveAction)
        onSourcesClick?.let { sources ->
            add(DetailSecondaryAction(label = sourcesLabel, icon = Icons.Rounded.Layers, onClick = sources))
        }
        add(watchedAction)
        onDownloadClick?.let { download ->
            add(
                DetailSecondaryAction(
                    label = downloadLabel,
                    icon = Icons.Rounded.Download,
                    onClick = download,
                    shortLabel = downloadShort,
                ),
            )
        }
        shuffleAction?.let(::add)
        onPlayExternallyClick?.let { playExternally ->
            add(
                DetailSecondaryAction(
                    label = externalLabel,
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = playExternally,
                    shortLabel = externalShort,
                ),
            )
        }
    }
    return DetailActionSet(shuffleAction, watchedAction, saveAction, iconActions)
}

@Composable
fun TabletDetailHeroActions(
    playLabel: String,
    playEnabled: Boolean,
    isSaved: Boolean,
    isWatched: Boolean,
    shuffleEnabled: Boolean,
    landscape: Boolean,
    onPlayClick: () -> Unit,
    onPlayLongClick: (() -> Unit)?,
    onShuffleClick: (() -> Unit)?,
    onWatchedClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onDownloadClick: (() -> Unit)? = null,
    onPlayFromStartClick: (() -> Unit)? = null,
    onPlayExternallyClick: (() -> Unit)? = null,
    userRating: Int? = null,
    onRateClick: (() -> Unit)? = null,
    resumeProgress: Float? = null,
    onSourcesClick: (() -> Unit)? = null,
    onQuickRate: ((Int?) -> Unit)? = null,
) {
    val actionSet = detailActionSet(
        isSaved = isSaved,
        isWatched = isWatched,
        shuffleEnabled = shuffleEnabled,
        onShuffleClick = onShuffleClick,
        onWatchedClick = onWatchedClick,
        onSaveClick = onSaveClick,
        onSaveLongClick = onSaveLongClick,
        onDownloadClick = onDownloadClick,
        onPlayFromStartClick = onPlayFromStartClick,
        onPlayExternallyClick = onPlayExternallyClick,
        onSourcesClick = onSourcesClick,
    )
    val playPainter = appIconPainter(AppIconResource.PlayerPlay)
    val buttonHeight = 56.dp
    val playShape = RoundedCornerShape(40.dp)
    val resolvedPlayLabel = if (playEnabled) playLabel else stringResource(Res.string.playback_unavailable)

    if (landscape) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        resumeProgress?.let { ResumeAvailableLine(it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TabletHeroActionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayButton(
                playLabel = resolvedPlayLabel,
                playEnabled = playEnabled,
                playPainter = playPainter,
                playShape = playShape,
                buttonHeight = buttonHeight,
                isTablet = true,
                onPlayClick = onPlayClick,
                onPlayLongClick = onPlayLongClick,
                modifier = Modifier.weight(1f),
            )
            if (onRateClick != null) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    TitleRatingControl(rating = userRating, onRateClick = onRateClick, onQuickRate = onQuickRate)
                }
                DetailIconActionRow(
                    actions = actionSet.icons,
                    preferredSize = buttonHeight,
                    preferredSpacing = 16.dp,
                    modifier = Modifier.weight(1f),
                )
            } else {
                DetailIconActionRow(
                    actions = actionSet.icons,
                    preferredSize = buttonHeight,
                    preferredSpacing = 16.dp,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(2f),
                )
            }
        }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            resumeProgress?.let { progress ->
                Box(Modifier.widthIn(max = TabletPortraitActionsMaxWidth).fillMaxWidth()) { ResumeAvailableLine(progress) }
            }
            PlayButton(
                playLabel = resolvedPlayLabel,
                playEnabled = playEnabled,
                playPainter = playPainter,
                playShape = playShape,
                buttonHeight = buttonHeight,
                isTablet = true,
                onPlayClick = onPlayClick,
                onPlayLongClick = onPlayLongClick,
                modifier = Modifier
                    .widthIn(max = TabletPortraitActionsMaxWidth)
                    .fillMaxWidth(),
            )
            DetailIconActionRow(
                actions = actionSet.icons,
                preferredSize = buttonHeight,
                preferredSpacing = 20.dp,
                modifier = Modifier
                    .widthIn(max = TabletPortraitActionsMaxWidth)
                    .fillMaxWidth(),
            )
            onRateClick?.let { rate ->
                TitleRatingControl(rating = userRating, onRateClick = rate, onQuickRate = onQuickRate)
            }
        }
    }
}

private val TabletHeroActionGap = 24.dp
private val TabletPortraitActionsMaxWidth = 520.dp
private val TabletRatingStarSize = 34.dp

@Composable
private fun DetailIconActionRow(
    actions: List<DetailSecondaryAction>,
    preferredSize: Dp,
    preferredSpacing: Dp,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    if (actions.isEmpty()) {
        Spacer(modifier = modifier)
        return
    }
    // Better Nuvio: round glass buttons with their name underneath, like the TV page.
    BoxWithConstraints(modifier = modifier) {
        val layout = fitIconActionRow(
            count = actions.size,
            availableWidth = maxWidth,
            preferredSize = preferredSize,
            preferredSpacing = preferredSpacing,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (layout.fits) Modifier else Modifier.horizontalScroll(rememberScrollState())),
            horizontalArrangement = Arrangement.spacedBy(
                space = 0.dp,
                alignment = horizontalAlignment,
            ),
            verticalAlignment = Alignment.Top,
        ) {
            actions.forEach { action ->
                Column(
                    modifier = Modifier.width(layout.cellWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DetailIconAction(
                        label = action.label,
                        icon = action.icon,
                        drawable = action.drawable,
                        active = action.isActive,
                        progress = 1f,
                        size = layout.size,
                        onClick = action.onClick,
                        onLongClick = action.onLongClick,
                    )
                    Text(
                        text = action.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (action.isActive) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** "Retomada disponível · 38%" above the main button, as on the TV page. */
@Composable
private fun ResumeAvailableLine(progress: Float) {
    Text(
        text = stringResource(Res.string.bn_resume_available, (progress * 100f).roundToInt().coerceIn(1, 99)),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val QuickRatingValues = listOf(3, 7, 10)

private fun quickRatingIndex(rating: Int?): Int? = when {
    rating == null -> null
    rating <= 4 -> 0
    rating <= 8 -> 1
    else -> 2
}

/**
 * Better Nuvio: "Não curti / Curti / Amei" (TV v4). Each segment writes a 1–10 rating
 * (3 / 7 / 10) to the connected services; the same segment again removes it. A long press
 * on Curti opens the full 1–10 sheet. Without [onQuickRate] it falls back to the stars.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TitleRatingControl(
    rating: Int?,
    onRateClick: () -> Unit,
    onQuickRate: ((Int?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (onQuickRate == null) {
        UserRatingStars(rating = rating, onClick = onRateClick, modifier = modifier)
        return
    }
    val hapticFeedback = LocalHapticFeedback.current
    val selected = quickRatingIndex(rating)
    val accent = corViva()
    val shape = RoundedCornerShape(40.dp)
    val labels = listOf(
        stringResource(Res.string.bn_rating_disliked),
        stringResource(Res.string.bn_rating_liked),
        stringResource(Res.string.bn_rating_loved),
    )
    val icons = listOf(Icons.Rounded.ThumbDown, Icons.Rounded.ThumbUp, Icons.Rounded.Favorite)
    val hint = stringResource(Res.string.bn_rating_long_press_hint)
    Row(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .ambientLiquidGlass(
                shape = shape,
                tint = BetterNuvioGlass,
                fallback = BetterNuvioGlass,
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = selected == index
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(if (isSelected) accent else Color.Transparent)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = if (index == 1) hint else null,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onQuickRate(if (isSelected) null else QuickRatingValues[index])
                        },
                        onLongClick = if (index == 1) {
                            {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRateClick()
                            }
                        } else {
                            null
                        },
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val contentColor = if (isSelected) accent.onCorViva() else MaterialTheme.colorScheme.onSurface
                Icon(
                    imageVector = icons[index],
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Glass of the TV (38, 38, 44 at 50%). */
private val BetterNuvioGlass = Color(red = 38, green = 38, blue = 44, alpha = 128)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DetailIconAction(
    label: String,
    icon: ImageVector,
    active: Boolean,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp,
    drawable: DrawableResource? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val glassActive = isAmbientLiquidGlassActive()
    val ring = corViva()
    Surface(
        modifier = modifier
            .graphicsLayer {
                alpha = progress
                scaleX = 0.86f + (0.14f * progress)
                scaleY = 0.86f + (0.14f * progress)
            }
            .then(
                if (glassActive) {
                    Modifier.ambientLiquidGlass(
                        shape = CircleShape,
                        tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        fallback = Color.Transparent,
                    )
                } else {
                    Modifier
                },
            )
            .then(if (active) Modifier.border(2.dp, ring, CircleShape) else Modifier),
        shape = CircleShape,
        color = if (glassActive) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    role = Role.Button,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (drawable != null) {
                Icon(
                    painter = painterResource(drawable),
                    contentDescription = label,
                    modifier = Modifier.size(21.dp),
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayButton(
    playLabel: String,
    playEnabled: Boolean,
    playPainter: Painter,
    playShape: Shape,
    buttonHeight: Dp,
    isTablet: Boolean,
    onPlayClick: () -> Unit,
    onPlayLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
        // Better Nuvio: the main button is filled with the title's cor viva.
        val playColor = corViva()
        Surface(
            modifier = modifier
                .height(buttonHeight),
            shape = playShape,
            color = if (playEnabled) playColor else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (playEnabled) {
                playColor.onCorViva()
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        enabled = playEnabled,
                        onClick = {
                            onPlayClick()
                        },
                        onLongClick = onPlayLongClick,
                        role = Role.Button,
                    )
                    .height(buttonHeight),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = playPainter,
                    contentDescription = null,
                    modifier = Modifier.size(if (isTablet) 20.dp else 18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = playLabel,
                    style = if (isTablet) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
}

private data class IconActionRowLayout(val size: Dp, val cellWidth: Dp, val fits: Boolean)

private fun fitIconActionRow(
    count: Int,
    availableWidth: Dp,
    preferredSize: Dp,
    preferredSpacing: Dp,
): IconActionRowLayout {
    val minCell = 62.dp
    val maxCell = preferredSize + preferredSpacing + 24.dp
    if (count <= 0) return IconActionRowLayout(preferredSize, maxCell, true)
    val evenCell = availableWidth / count
    return if (evenCell >= minCell) {
        val cell = if (evenCell < maxCell) evenCell else maxCell
        val size = if (cell - 12.dp < preferredSize) cell - 12.dp else preferredSize
        IconActionRowLayout(if (size < 44.dp) 44.dp else size, cell, true)
    } else {
        IconActionRowLayout(48.dp, 68.dp, false)
    }
}
