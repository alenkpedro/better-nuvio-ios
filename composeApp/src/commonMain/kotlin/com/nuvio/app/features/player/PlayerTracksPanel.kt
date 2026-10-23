package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.corViva
import com.nuvio.app.core.ui.onCorViva
import com.nuvio.app.features.autosync.AutoSyncSyncedSubtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.addon_title
import nuvio.composeapp.generated.resources.bn_back
import nuvio.composeapp.generated.resources.bn_decimal_separator
import nuvio.composeapp.generated.resources.bn_player_subtitle_synced
import nuvio.composeapp.generated.resources.bn_subtitle_background
import nuvio.composeapp.generated.resources.bn_subtitle_background_full
import nuvio.composeapp.generated.resources.bn_subtitle_background_half
import nuvio.composeapp.generated.resources.bn_subtitle_background_none
import nuvio.composeapp.generated.resources.bn_subtitle_color
import nuvio.composeapp.generated.resources.bn_subtitle_font_default_tag
import nuvio.composeapp.generated.resources.bn_subtitle_font_header
import nuvio.composeapp.generated.resources.bn_subtitle_fps
import nuvio.composeapp.generated.resources.bn_subtitle_more_options
import nuvio.composeapp.generated.resources.bn_subtitle_recommended
import nuvio.composeapp.generated.resources.bn_subtitle_restore
import nuvio.composeapp.generated.resources.bn_subtitle_same_file
import nuvio.composeapp.generated.resources.bn_subtitle_same_release
import nuvio.composeapp.generated.resources.bn_subtitle_see_all
import nuvio.composeapp.generated.resources.bn_subtitle_size
import nuvio.composeapp.generated.resources.bn_subtitle_size_l
import nuvio.composeapp.generated.resources.bn_subtitle_size_m
import nuvio.composeapp.generated.resources.bn_subtitle_size_s
import nuvio.composeapp.generated.resources.bn_subtitle_size_xl
import nuvio.composeapp.generated.resources.bn_subtitle_style
import nuvio.composeapp.generated.resources.bn_subtitle_synced_tag
import nuvio.composeapp.generated.resources.bn_subtitles_off
import nuvio.composeapp.generated.resources.bn_sync_auto_hint
import nuvio.composeapp.generated.resources.bn_sync_auto_pick_addon
import nuvio.composeapp.generated.resources.bn_sync_ear_hint
import nuvio.composeapp.generated.resources.bn_sync_ear_start
import nuvio.composeapp.generated.resources.bn_sync_mode_auto
import nuvio.composeapp.generated.resources.bn_sync_mode_ear
import nuvio.composeapp.generated.resources.bn_sync_mode_manual
import nuvio.composeapp.generated.resources.bn_sync_status_synced
import nuvio.composeapp.generated.resources.bn_tracks_header_audio
import nuvio.composeapp.generated.resources.bn_tracks_header_subtitles
import nuvio.composeapp.generated.resources.bn_tracks_header_sync
import nuvio.composeapp.generated.resources.bn_tracks_no_audio
import nuvio.composeapp.generated.resources.compose_player_built_in
import nuvio.composeapp.generated.resources.compose_player_fetch_subtitles
import nuvio.composeapp.generated.resources.compose_player_reset
import nuvio.composeapp.generated.resources.settings_playback_option_forced
import nuvio.composeapp.generated.resources.settings_playback_subtitle_font_failed
import nuvio.composeapp.generated.resources.settings_playback_subtitle_font_import
import nuvio.composeapp.generated.resources.settings_playback_subtitle_font_invalid
import nuvio.composeapp.generated.resources.settings_playback_subtitle_font_too_large
import nuvio.composeapp.generated.resources.subtitle_language_unknown
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * Better Nuvio: Audio | Subtitles | Sync side by side in a panel that rises from the
 * bottom, with the video still visible above. "Subtitle style" swaps the panel content.
 * Subtitles are grouped by language like the TV, with the Recommended one (the one that
 * matches the video file name and fps) first and the rest behind "See all N".
 */

private val PanelBackground = Color(0xE018181C)
private val Ink2 = Color.White.copy(alpha = 0.72f)
private val Ink3 = Color.White.copy(alpha = 0.5f)
private val SyncedGreen = Color(0xFF2FA84F)
private const val SubtitleGroupPreviewCount = 3

private enum class TracksPanelPage { Main, Style, MoreStyle }
private enum class SubtitleSyncMode { Auto, Ear, Manual }

private val SubtitleSizeSteps = listOf(14, 18, 22, 26)
private val SubtitleQuickColors = listOf(
    Color.White,
    Color(0xFFF5D742),
    Color(0xFF7FD6FF),
    Color(0xFF9BE38A),
)

@Composable
internal fun PlayerTracksPanel(
    visible: Boolean,
    audioTracks: List<AudioTrack>,
    selectedAudioIndex: Int,
    onAudioTrackSelected: (Int) -> Unit,
    subtitleTracks: List<SubtitleTrack>,
    selectedSubtitleIndex: Int,
    addonSubtitles: List<AddonSubtitle>,
    selectedAddonSubtitleId: String?,
    isLoadingAddonSubtitles: Boolean,
    preferredSubtitleLanguage: String,
    secondaryPreferredSubtitleLanguage: String?,
    subtitleStyle: SubtitleStyleState,
    subtitleDelayMs: Int,
    selectedAddonSubtitle: AddonSubtitle?,
    subtitleAutoSyncState: SubtitleAutoSyncUiState,
    videoSource: String?,
    videoFps: Double?,
    onBuiltInTrackSelected: (Int) -> Unit,
    onAddonSubtitleSelected: (AddonSubtitle) -> Unit,
    onFetchAddonSubtitles: () -> Unit,
    onStyleChanged: (SubtitleStyleState) -> Unit,
    onSubtitleDelayChanged: (Int) -> Unit,
    onSubtitleDelayReset: () -> Unit,
    onSyncByEarClick: () -> Unit,
    onAutoSyncCapture: () -> Unit,
    onAutoSyncCueSelected: (SubtitleSyncCue) -> Unit,
    onAutoSyncReload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformBackHandler(enabled = visible, onBack = onDismiss)
    var page by remember(visible) { mutableStateOf(TracksPanelPage.Main) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(180)),
        modifier = modifier,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val panelHeight = minOf(300.dp, maxHeight - 56.dp).coerceAtLeast(200.dp)
            // Tapping the video above the panel closes it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .animateEnterExit(
                        enter = slideInVertically(tween(260)) { it },
                        exit = slideOutVertically(tween(200)) { it },
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 40.dp)
                        .fillMaxWidth()
                        .height(panelHeight)
                        .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                        .background(PanelBackground)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    PanelDragHandle(onDismiss = onDismiss)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                    ) {
                        when (page) {
                            TracksPanelPage.Main -> Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                AudioColumn(
                                    audioTracks = audioTracks,
                                    selectedAudioIndex = selectedAudioIndex,
                                    onAudioTrackSelected = onAudioTrackSelected,
                                    modifier = Modifier.weight(0.8f),
                                )
                                SubtitleGroupsColumn(
                                    visible = visible,
                                    subtitleTracks = subtitleTracks,
                                    selectedSubtitleIndex = selectedSubtitleIndex,
                                    addonSubtitles = addonSubtitles,
                                    selectedAddonSubtitleId = selectedAddonSubtitleId,
                                    selectedAddonSubtitle = selectedAddonSubtitle,
                                    isLoadingAddonSubtitles = isLoadingAddonSubtitles,
                                    preferredSubtitleLanguage = preferredSubtitleLanguage,
                                    secondaryPreferredSubtitleLanguage = secondaryPreferredSubtitleLanguage,
                                    showOnlyPreferredLanguages = subtitleStyle.showOnlyPreferredLanguages,
                                    videoSource = videoSource,
                                    videoFps = videoFps,
                                    onBuiltInTrackSelected = onBuiltInTrackSelected,
                                    onAddonSubtitleSelected = onAddonSubtitleSelected,
                                    onFetchAddonSubtitles = onFetchAddonSubtitles,
                                    modifier = Modifier.weight(1.3f),
                                )
                                SyncColumn(
                                    visible = visible,
                                    subtitleDelayMs = subtitleDelayMs,
                                    selectedAddonSubtitle = selectedAddonSubtitle
                                        ?: addonSubtitles.findSelectedAddon(selectedAddonSubtitleId),
                                    subtitleStyle = subtitleStyle,
                                    onSubtitleDelayChanged = onSubtitleDelayChanged,
                                    onSubtitleDelayReset = onSubtitleDelayReset,
                                    onSyncByEarClick = {
                                        onSyncByEarClick()
                                        onDismiss()
                                    },
                                    onStyleClick = { page = TracksPanelPage.Style },
                                    modifier = Modifier.weight(1.1f),
                                )
                            }

                            TracksPanelPage.Style -> SubtitleStylePage(
                                style = subtitleStyle,
                                onStyleChanged = onStyleChanged,
                                onBack = { page = TracksPanelPage.Main },
                                onMoreOptions = { page = TracksPanelPage.MoreStyle },
                            )

                            TracksPanelPage.MoreStyle -> Column(modifier = Modifier.fillMaxSize()) {
                                PanelHeaderRow(
                                    title = stringResource(Res.string.bn_subtitle_more_options),
                                    onBack = { page = TracksPanelPage.Style },
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    SubtitleStylePanel(
                                        style = subtitleStyle,
                                        subtitleDelayMs = subtitleDelayMs,
                                        selectedAddonSubtitle = selectedAddonSubtitle
                                            ?: addonSubtitles.findSelectedAddon(selectedAddonSubtitleId),
                                        subtitleAutoSyncState = subtitleAutoSyncState,
                                        isCompact = true,
                                        showHeader = false,
                                        onStyleChanged = onStyleChanged,
                                        onSubtitleDelayChanged = onSubtitleDelayChanged,
                                        onSubtitleDelayReset = onSubtitleDelayReset,
                                        onSyncByEarClick = {
                                            onSyncByEarClick()
                                            onDismiss()
                                        },
                                        onAutoSyncCapture = onAutoSyncCapture,
                                        onAutoSyncCueSelected = onAutoSyncCueSelected,
                                        onAutoSyncReload = onAutoSyncReload,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelDragHandle(onDismiss: () -> Unit) {
    var dragged by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged > 40.dp.toPx()) onDismiss() },
                    onDragCancel = { dragged = 0f },
                ) { change, amount ->
                    change.consume()
                    dragged += amount
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.3f)),
        )
    }
}

@Composable
private fun ColumnHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            color = Ink3,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun PanelHeaderRow(title: String, onBack: () -> Unit) {
    ColumnHeader(text = title) {
        Text(
            text = "‹ " + stringResource(Res.string.bn_back),
            color = Ink2,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Selected rows: cor viva at 30% with a 70% border and a white check. */
private fun Modifier.selectableRow(selected: Boolean, accent: Color): Modifier {
    val shape = RoundedCornerShape(12.dp)
    return clip(shape).then(
        if (selected) {
            Modifier
                .background(accent.copy(alpha = 0.3f))
                .border(1.dp, accent.copy(alpha = 0.7f), shape)
        } else {
            Modifier
        },
    )
}

@Composable
private fun AudioColumn(
    audioTracks: List<AudioTrack>,
    selectedAudioIndex: Int,
    onAudioTrackSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = corViva()
    Column(modifier = modifier.fillMaxHeight()) {
        ColumnHeader(text = stringResource(Res.string.bn_tracks_header_audio))
        if (audioTracks.isEmpty()) {
            Text(
                text = stringResource(Res.string.bn_tracks_no_audio),
                color = Ink3,
                fontSize = 12.5.sp,
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(audioTracks, key = { position, track -> "audio:$position:${track.index}" }) { _, track ->
                val selected = track.index == selectedAudioIndex ||
                    (selectedAudioIndex < 0 && track.isSelected)
                val title = track.language?.takeIf { it.isNotBlank() && it != "und" }
                    ?.let { languageLabelForCode(it) }
                    ?: localizedTrackDisplayName(track.label, track.language, track.index)
                val subtitle = track.label.takeIf { it.isNotBlank() && it != title }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableRow(selected, accent)
                        .clickableIncludingFlingStop { onAudioTrackSelected(track.index) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        subtitle?.let {
                            Text(
                                text = it,
                                color = Ink3,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (selected) SelectedCheck()
                }
            }
        }
    }
}

@Composable
private fun SelectedCheck() {
    Icon(
        imageVector = Icons.Rounded.Check,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
            .padding(start = 6.dp)
            .size(16.dp),
    )
}

private sealed interface SubtitleListRow {
    data object Off : SubtitleListRow
    data class Header(val item: SubtitleLanguageItem, val expanded: Boolean) : SubtitleListRow
    data class Option(
        val option: SubtitleSelectionOption,
        val recommended: Boolean,
        val evidence: SubtitleMatchEvidence?,
    ) : SubtitleListRow
    data class SeeAll(val languageKey: String, val total: Int) : SubtitleListRow
    data object Loading : SubtitleListRow
    data object Fetch : SubtitleListRow
}

@Composable
private fun SubtitleGroupsColumn(
    visible: Boolean,
    subtitleTracks: List<SubtitleTrack>,
    selectedSubtitleIndex: Int,
    addonSubtitles: List<AddonSubtitle>,
    selectedAddonSubtitleId: String?,
    selectedAddonSubtitle: AddonSubtitle?,
    isLoadingAddonSubtitles: Boolean,
    preferredSubtitleLanguage: String,
    secondaryPreferredSubtitleLanguage: String?,
    showOnlyPreferredLanguages: Boolean,
    videoSource: String?,
    videoFps: Double?,
    onBuiltInTrackSelected: (Int) -> Unit,
    onAddonSubtitleSelected: (AddonSubtitle) -> Unit,
    onFetchAddonSubtitles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectiveSelectedAddon = selectedAddonSubtitle ?: addonSubtitles.findSelectedAddon(selectedAddonSubtitleId)
    val playbackLanguageKey = selectedSubtitleLanguageKey(subtitleTracks, selectedSubtitleIndex, effectiveSelectedAddon)
    val playbackOptionId = selectedSubtitleOptionId(subtitleTracks, selectedSubtitleIndex, effectiveSelectedAddon)
    val subtitleStructureKey = subtitleTracksStructureKey(subtitleTracks)
    val addonStructureKey = addonSubtitlesStructureKey(addonSubtitles)
    val languageItems = remember(
        subtitleStructureKey,
        addonStructureKey,
        preferredSubtitleLanguage,
        secondaryPreferredSubtitleLanguage,
        showOnlyPreferredLanguages,
        playbackLanguageKey,
    ) {
        buildSubtitleLanguageItems(
            subtitleTracks = subtitleTracks,
            addonSubtitles = addonSubtitles,
            preferredLanguage = preferredSubtitleLanguage,
            secondaryPreferredLanguage = secondaryPreferredSubtitleLanguage,
            showOnlyPreferredLanguages = showOnlyPreferredLanguages,
            selectedLanguageKey = playbackLanguageKey,
        )
    }
    // The language in use (or the preferred one) opens by default.
    val expanded = remember(visible) { mutableStateMapOf<String, Boolean>() }
    val showAll = remember(visible) { mutableStateListOf<String>() }
    var pendingOptionId by remember(visible) { mutableStateOf(playbackOptionId) }
    LaunchedEffect(playbackOptionId) { pendingOptionId = playbackOptionId }
    val selectedOptionId = pendingOptionId ?: playbackOptionId
    val defaultOpenKey = playbackLanguageKey.takeIf { it != SubtitleOffLanguageKey }
        ?: languageItems.firstOrNull { it.key != SubtitleOffLanguageKey }?.key

    val rows = remember(
        languageItems,
        subtitleStructureKey,
        addonStructureKey,
        expanded.toMap(),
        showAll.toList(),
        selectedOptionId,
        defaultOpenKey,
        videoSource,
        videoFps,
        isLoadingAddonSubtitles,
    ) {
        buildList {
            add(SubtitleListRow.Off)
            languageItems.filter { it.key != SubtitleOffLanguageKey }.forEach { item ->
                val isOpen = expanded[item.key] ?: (item.key == defaultOpenKey)
                add(SubtitleListRow.Header(item, isOpen))
                if (!isOpen) return@forEach
                val options = buildSubtitleSelectionOptions(item.key, subtitleTracks, addonSubtitles)
                val groupAddons = options.mapNotNull { (it as? SubtitleSelectionOption.Addon)?.subtitle }
                val recommended = groupAddons.recommendedSubtitle(videoSource, videoFps)
                val ordered = if (recommended != null) {
                    val recommendedOption = options.first {
                        it is SubtitleSelectionOption.Addon && it.subtitle == recommended
                    }
                    listOf(recommendedOption) + options.filter { it !== recommendedOption }
                } else {
                    options
                }
                val visibleOptions = if (item.key in showAll || ordered.size <= SubtitleGroupPreviewCount) {
                    ordered
                } else {
                    val preview = ordered.take(SubtitleGroupPreviewCount)
                    val selected = ordered.firstOrNull { it.id == selectedOptionId }
                    if (selected != null && selected !in preview) preview + selected else preview
                }
                visibleOptions.forEach { option ->
                    val addon = (option as? SubtitleSelectionOption.Addon)?.subtitle
                    add(
                        SubtitleListRow.Option(
                            option = option,
                            recommended = addon != null && addon == recommended,
                            evidence = addon?.matchEvidence(videoSource, videoFps),
                        ),
                    )
                }
                if (visibleOptions.size < ordered.size) {
                    add(SubtitleListRow.SeeAll(item.key, ordered.size))
                }
            }
            if (addonSubtitles.isEmpty()) {
                add(if (isLoadingAddonSubtitles) SubtitleListRow.Loading else SubtitleListRow.Fetch)
            }
        }
    }

    val accent = corViva()
    val syncedUrl by AutoSyncSyncedSubtitle.url.collectAsState()

    Column(modifier = modifier.fillMaxHeight()) {
        ColumnHeader(text = stringResource(Res.string.bn_tracks_header_subtitles))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            itemsIndexed(rows, key = { index, row -> subtitleRowKey(index, row) }) { _, row ->
                when (row) {
                    SubtitleListRow.Off -> {
                        val selected = playbackLanguageKey == SubtitleOffLanguageKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableRow(selected, accent)
                                .clickableIncludingFlingStop {
                                    pendingOptionId = null
                                    onBuiltInTrackSelected(-1)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(Res.string.bn_subtitles_off),
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) SelectedCheck()
                        }
                    }

                    is SubtitleListRow.Header -> SubtitleLanguageHeader(
                        item = row.item,
                        expanded = row.expanded,
                        onClick = { expanded[row.item.key] = !row.expanded },
                    )

                    is SubtitleListRow.Option -> SubtitleFileRow(
                        option = row.option,
                        recommended = row.recommended,
                        evidence = row.evidence,
                        synced = (row.option as? SubtitleSelectionOption.Addon)?.subtitle?.url
                            ?.let { it == syncedUrl } == true,
                        selected = row.option.id == selectedOptionId,
                        accent = accent,
                        onClick = {
                            pendingOptionId = row.option.id
                            when (val option = row.option) {
                                is SubtitleSelectionOption.BuiltIn -> onBuiltInTrackSelected(option.track.index)
                                is SubtitleSelectionOption.Addon -> onAddonSubtitleSelected(option.subtitle)
                            }
                        },
                    )

                    is SubtitleListRow.SeeAll -> Text(
                        text = stringResource(Res.string.bn_subtitle_see_all, row.total) + " ›",
                        color = Ink2,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickableIncludingFlingStop { showAll.add(row.languageKey) }
                            .padding(start = 32.dp, top = 6.dp, bottom = 6.dp),
                    )

                    SubtitleListRow.Loading -> PlayerModalLoading(modifier = Modifier.padding(vertical = 12.dp))

                    SubtitleListRow.Fetch -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onFetchAddonSubtitles)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDownload,
                            contentDescription = null,
                            tint = Ink2,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(Res.string.compose_player_fetch_subtitles),
                            color = Ink2,
                            fontSize = 12.5.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun subtitleRowKey(index: Int, row: SubtitleListRow): String = when (row) {
    SubtitleListRow.Off -> "off"
    is SubtitleListRow.Header -> "lang:${row.item.key}"
    is SubtitleListRow.Option -> "opt:$index:${row.option.id}"
    is SubtitleListRow.SeeAll -> "all:${row.languageKey}"
    SubtitleListRow.Loading -> "loading"
    SubtitleListRow.Fetch -> "fetch"
}

@Composable
private fun SubtitleLanguageHeader(
    item: SubtitleLanguageItem,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val label = when (item.key) {
        SubtitleUnknownLanguageKey -> stringResource(Res.string.subtitle_language_unknown)
        else -> languageLabelForCode(item.key)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickableIncludingFlingStop(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = item.count.toString(),
            color = Ink2,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .padding(horizontal = 7.dp, vertical = 1.dp),
        )
        Spacer(modifier = Modifier.weight(0.001f))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Ink3,
            modifier = Modifier
                .size(18.dp)
                .rotate(if (expanded) 90f else 0f),
        )
    }
}

@Composable
private fun SubtitleFileRow(
    option: SubtitleSelectionOption,
    recommended: Boolean,
    evidence: SubtitleMatchEvidence?,
    synced: Boolean,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val decimal = stringResource(Res.string.bn_decimal_separator)
    val title: String
    val sourceParts = mutableListOf<String>()
    when (option) {
        is SubtitleSelectionOption.BuiltIn -> {
            title = localizedTrackDisplayName(option.track.label, option.track.language, option.track.index)
            sourceParts += stringResource(Res.string.compose_player_built_in)
            if (option.track.isForced) sourceParts += stringResource(Res.string.settings_playback_option_forced)
        }

        is SubtitleSelectionOption.Addon -> {
            val subtitle = option.subtitle
            title = subtitle.fileName ?: subtitle.releaseName ?: subtitle.display
            sourceParts += subtitle.addonName ?: stringResource(Res.string.addon_title)
            when {
                evidence?.sameFile == true -> sourceParts += stringResource(Res.string.bn_subtitle_same_file)
                evidence != null -> sourceParts += stringResource(Res.string.bn_subtitle_same_release)
            }
            if (synced) sourceParts += stringResource(Res.string.bn_subtitle_synced_tag)
            subtitle.fps?.takeIf { it > 0 }?.let {
                sourceParts += stringResource(Res.string.bn_subtitle_fps, formatFps(it, decimal))
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp)
            .selectableRow(selected, accent)
            .clickableIncludingFlingStop(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (recommended) {
                    Text(
                        text = stringResource(Res.string.bn_subtitle_recommended).uppercase(),
                        color = Color(0xFF0A0A0B),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                text = sourceParts.joinToString(" · "),
                color = Ink3,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) SelectedCheck()
    }
}

private fun formatFps(fps: Double, decimal: String): String {
    val milli = (fps * 1000).roundToLong()
    val whole = milli / 1000
    val fraction = (milli % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole$decimal$fraction"
}

internal fun formatSubtitleDelaySeconds(delayMs: Int, decimal: String): String {
    val tenths = (abs(delayMs) + 50) / 100
    val sign = when {
        tenths == 0 -> ""
        delayMs > 0 -> "+"
        else -> "−"
    }
    return "$sign${tenths / 10}$decimal${tenths % 10} s"
}

@Composable
private fun SyncColumn(
    visible: Boolean,
    subtitleDelayMs: Int,
    selectedAddonSubtitle: AddonSubtitle?,
    subtitleStyle: SubtitleStyleState,
    onSubtitleDelayChanged: (Int) -> Unit,
    onSubtitleDelayReset: () -> Unit,
    onSyncByEarClick: () -> Unit,
    onStyleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val syncedUrl by AutoSyncSyncedSubtitle.url.collectAsState()
    val isAutoSynced = selectedAddonSubtitle != null && selectedAddonSubtitle.url == syncedUrl
    var mode by remember(visible) {
        mutableStateOf(
            if (subtitleDelayMs != 0 && !isAutoSynced) SubtitleSyncMode.Manual else SubtitleSyncMode.Auto,
        )
    }
    val decimal = stringResource(Res.string.bn_decimal_separator)

    Column(modifier = modifier.fillMaxHeight()) {
        ColumnHeader(text = stringResource(Res.string.bn_tracks_header_sync))
        SegmentedChoice(
            labels = listOf(
                stringResource(Res.string.bn_sync_mode_auto),
                stringResource(Res.string.bn_sync_mode_ear),
                stringResource(Res.string.bn_sync_mode_manual),
            ),
            selectedIndex = mode.ordinal,
            onSelected = { mode = SubtitleSyncMode.entries[it] },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (mode) {
                SubtitleSyncMode.Auto -> {
                    if (isAutoSynced) {
                        SyncedStatusLine(
                            text = stringResource(
                                Res.string.bn_sync_status_synced,
                                formatSubtitleDelaySeconds(subtitleDelayMs, decimal),
                            ),
                        )
                    }
                    HintText(
                        stringResource(
                            if (selectedAddonSubtitle == null) Res.string.bn_sync_auto_pick_addon
                            else Res.string.bn_sync_auto_hint,
                        ),
                    )
                }

                SubtitleSyncMode.Ear -> {
                    HintText(stringResource(Res.string.bn_sync_ear_hint))
                    PanelButton(
                        text = stringResource(Res.string.bn_sync_ear_start),
                        filled = true,
                        onClick = onSyncByEarClick,
                    )
                }

                SubtitleSyncMode.Manual -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PanelButton(
                            text = "−0${decimal}1 s",
                            onClick = {
                                onSubtitleDelayChanged(
                                    (subtitleDelayMs - SUBTITLE_DELAY_STEP_MS)
                                        .coerceIn(SUBTITLE_DELAY_MIN_MS, SUBTITLE_DELAY_MAX_MS),
                                )
                            },
                        )
                        Text(
                            text = formatSubtitleDelaySeconds(subtitleDelayMs, decimal),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                        PanelButton(
                            text = "+0${decimal}1 s",
                            onClick = {
                                onSubtitleDelayChanged(
                                    (subtitleDelayMs + SUBTITLE_DELAY_STEP_MS)
                                        .coerceIn(SUBTITLE_DELAY_MIN_MS, SUBTITLE_DELAY_MAX_MS),
                                )
                            },
                        )
                    }
                    if (subtitleDelayMs != 0) {
                        Text(
                            text = stringResource(Res.string.compose_player_reset),
                            color = Ink2,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onSubtitleDelayReset)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        // "Subtitle style   Netflix Sans ›"
        val fontLabel = BuiltInSubtitleFont.resolve(subtitleStyle.customFontFile)?.label
            ?: SubtitleFontRepository.fonts.collectAsState().value
                .firstOrNull { it.fileName == subtitleStyle.customFontFile }?.familyName
            ?: BuiltInSubtitleFont.DEFAULT.label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onStyleClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.bn_subtitle_style),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$fontLabel ›",
                color = Ink2,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SyncedStatusLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(SyncedGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Text(text = text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HintText(text: String) {
    Text(text = text, color = Ink3, fontSize = 11.5.sp, lineHeight = 15.sp)
}

@Composable
private fun PanelButton(text: String, onClick: () -> Unit, filled: Boolean = false) {
    val accent = corViva()
    Text(
        text = text,
        color = if (filled) accent.onCorViva() else Color.White,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) accent else Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun SegmentedChoice(
    labels: List<String>,
    selectedIndex: Int?,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = corViva()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) accent else Color.Transparent)
                    .clickable { onSelected(index) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (active) accent.onCorViva() else Ink2,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SubtitleStylePage(
    style: SubtitleStyleState,
    onStyleChanged: (SubtitleStyleState) -> Unit,
    onBack: () -> Unit,
    onMoreOptions: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        SubtitleFontGrid(
            style = style,
            onStyleChanged = onStyleChanged,
            onBack = onBack,
            modifier = Modifier.weight(1.3f),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ColumnHeader(text = stringResource(Res.string.bn_subtitle_size), modifier = Modifier.padding(top = 0.dp))
            SegmentedChoice(
                labels = listOf(
                    stringResource(Res.string.bn_subtitle_size_s),
                    stringResource(Res.string.bn_subtitle_size_m),
                    stringResource(Res.string.bn_subtitle_size_l),
                    stringResource(Res.string.bn_subtitle_size_xl),
                ),
                selectedIndex = SubtitleSizeSteps.indexOf(style.fontSizeSp).takeIf { it >= 0 },
                onSelected = { onStyleChanged(style.copy(fontSizeSp = SubtitleSizeSteps[it])) },
            )
            Spacer(modifier = Modifier.height(4.dp))
            ColumnHeader(text = stringResource(Res.string.bn_subtitle_background))
            val backgroundIndex = when {
                style.backgroundColor.alpha <= 0.01f -> 0
                abs(style.backgroundColor.alpha - 0.5f) < 0.06f && style.backgroundColor.red < 0.01f -> 1
                style.backgroundColor.alpha >= 0.99f && style.backgroundColor.red < 0.01f -> 2
                else -> null
            }
            SegmentedChoice(
                labels = listOf(
                    stringResource(Res.string.bn_subtitle_background_none),
                    stringResource(Res.string.bn_subtitle_background_half),
                    stringResource(Res.string.bn_subtitle_background_full),
                ),
                selectedIndex = backgroundIndex,
                onSelected = {
                    val color = when (it) {
                        0 -> Color.Transparent
                        1 -> Color.Black.copy(alpha = 0.5f)
                        else -> Color.Black
                    }
                    onStyleChanged(style.copy(backgroundColor = color))
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
            ColumnHeader(text = stringResource(Res.string.bn_subtitle_color))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SubtitleQuickColors.forEach { color ->
                    val selected = abs(color.red - style.textColor.red) < 0.01f &&
                        abs(color.green - style.textColor.green) < 0.01f &&
                        abs(color.blue - style.textColor.blue) < 0.01f
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (selected) Modifier.border(2.dp, corViva(), CircleShape) else Modifier,
                            )
                            .clickable { onStyleChanged(style.copy(textColor = color.copy(alpha = style.textColor.alpha))) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelButton(
                    text = stringResource(Res.string.bn_subtitle_restore),
                    onClick = {
                        onStyleChanged(
                            SubtitleStyleState.DEFAULT.copy(
                                stripSdh = style.stripSdh,
                                useForcedSubtitles = style.useForcedSubtitles,
                                showOnlyPreferredLanguages = style.showOnlyPreferredLanguages,
                            ),
                        )
                    },
                )
                PanelButton(
                    text = stringResource(Res.string.bn_subtitle_more_options) + " ›",
                    onClick = onMoreOptions,
                )
            }
        }
    }
}

@Composable
private fun SubtitleFontGrid(
    style: SubtitleStyleState,
    onStyleChanged: (SubtitleStyleState) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { SubtitleFontRepository.ensureLoaded() } }
    val fonts by SubtitleFontRepository.fonts.collectAsState()
    var importError by remember { mutableStateOf<StringResource?>(null) }
    val selectFont: (String) -> Unit = { onStyleChanged(style.copy(customFontFile = it)) }
    val importFont = rememberSubtitleFontImporter(
        onPicked = { fileName, bytes ->
            importError = when (val result = SubtitleFontRepository.import(fileName, bytes)) {
                is SubtitleFontImportResult.Imported -> {
                    selectFont(result.font.fileName)
                    null
                }
                SubtitleFontImportResult.NotAFont -> Res.string.settings_playback_subtitle_font_invalid
                SubtitleFontImportResult.TooLarge -> Res.string.settings_playback_subtitle_font_too_large
                SubtitleFontImportResult.Failed -> Res.string.settings_playback_subtitle_font_failed
            }
        },
        onError = { importError = Res.string.settings_playback_subtitle_font_failed },
    )
    val selected = style.customFontFile?.takeIf { file ->
        BuiltInSubtitleFont.fromId(file) != null || fonts.any { it.fileName == file }
    } ?: BuiltInSubtitleFont.DEFAULT.id
    val defaultTag = stringResource(Res.string.bn_subtitle_font_default_tag)

    data class FontCell(val id: String, val label: String, val family: FontFamily?, val isDefault: Boolean)

    val cells = BuiltInSubtitleFont.entries.map {
        FontCell(it.id, it.label, it.previewFamily(), it == BuiltInSubtitleFont.DEFAULT)
    } + fonts.map { FontCell(it.fileName, it.familyName, null, false) }

    Column(modifier = modifier.fillMaxHeight()) {
        PanelHeaderRow(title = stringResource(Res.string.bn_subtitle_font_header), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val accent = corViva()
            cells.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pair.forEach { cell ->
                        val isSelected = cell.id == selected
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .selectableRow(isSelected, accent)
                                .clickable { selectFont(cell.id) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = cell.label,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = cell.family,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (cell.isDefault) {
                                Text(
                                    text = defaultTag,
                                    color = Ink3,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                            Spacer(modifier = Modifier.weight(0.001f))
                            if (isSelected) SelectedCheck()
                        }
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
            Text(
                text = "+ " + stringResource(Res.string.settings_playback_subtitle_font_import),
                color = Ink2,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = importFont)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            importError?.let {
                Text(text = stringResource(it), color = Color(0xFFFF8A80), fontSize = 11.sp)
            }
        }
    }
}

/**
 * Better Nuvio: pílula verde "Legenda sincronizada · +1,2 s" no canto do player.
 * Só aparece quando a legenda de addon escolhida foi sincronizada sozinha.
 */
@Composable
internal fun PlayerSubtitleSyncedPill(
    selectedAddonSubtitle: AddonSubtitle?,
    subtitleDelayMs: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val syncedUrl by AutoSyncSyncedSubtitle.url.collectAsState()
    if (selectedAddonSubtitle == null || syncedUrl == null || selectedAddonSubtitle.url != syncedUrl) return
    val decimal = stringResource(Res.string.bn_decimal_separator)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(SyncedGreen)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(
            text = stringResource(
                Res.string.bn_player_subtitle_synced,
                formatSubtitleDelaySeconds(subtitleDelayMs, decimal),
            ),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
