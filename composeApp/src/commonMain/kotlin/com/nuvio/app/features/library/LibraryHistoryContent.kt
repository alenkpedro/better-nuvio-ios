package com.nuvio.app.features.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.bn_history_day_minutes
import nuvio.composeapp.generated.resources.bn_history_days
import nuvio.composeapp.generated.resources.bn_history_empty_message
import nuvio.composeapp.generated.resources.bn_history_empty_title
import nuvio.composeapp.generated.resources.bn_history_episode
import nuvio.composeapp.generated.resources.bn_history_episode_named
import nuvio.composeapp.generated.resources.bn_history_finished
import nuvio.composeapp.generated.resources.bn_history_finished_detail
import nuvio.composeapp.generated.resources.bn_history_hours_minutes
import nuvio.composeapp.generated.resources.bn_history_last_month
import nuvio.composeapp.generated.resources.bn_history_minutes
import nuvio.composeapp.generated.resources.bn_history_minutes_per_day
import nuvio.composeapp.generated.resources.bn_history_movie
import nuvio.composeapp.generated.resources.bn_history_record
import nuvio.composeapp.generated.resources.bn_history_streak
import nuvio.composeapp.generated.resources.bn_history_this_month
import nuvio.composeapp.generated.resources.bn_history_today
import nuvio.composeapp.generated.resources.bn_history_yesterday
import org.jetbrains.compose.resources.stringResource

/**
 * Better Nuvio: a aba Histórico da Biblioteca (historico.c na TV): os números do perfil em
 * cima e, embaixo, tudo o que foi assistido, do mais novo para o mais velho, por dia.
 */
internal fun LazyListScope.libraryHistoryContent(
    stats: WatchHistoryStats,
    feed: List<WatchHistoryFeedEntry>,
    today: LocalDate,
    onEntryClick: (WatchHistoryFeedEntry) -> Unit,
) {
    item(key = "history-stats") {
        WatchHistoryStatsBlock(
            stats = stats,
            today = today,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
    if (feed.isEmpty()) {
        item(key = "history-empty") {
            HomeEmptyStateCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                title = stringResource(Res.string.bn_history_empty_title),
                message = stringResource(Res.string.bn_history_empty_message),
            )
        }
        return
    }
    feed.groupBy { it.date }.forEach { (date, entries) ->
        item(key = "history-day-$date") {
            Text(
                text = historyDayLabel(date, today),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            )
        }
        items(entries, key = { "history-${it.entry.videoId}-${it.entry.lastUpdatedEpochMs}" }) { feedEntry ->
            WatchHistoryRow(feedEntry = feedEntry, onClick = { onEntryClick(feedEntry) })
        }
    }
}

@Composable
private fun historyDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(Res.string.bn_history_today)
    today.minus(1, DateTimeUnit.DAY) -> stringResource(Res.string.bn_history_yesterday)
    else -> date.toString().let { iso -> "${iso.substring(8, 10)}/${iso.substring(5, 7)}" }
}

@Composable
private fun formatWatchMinutes(minutes: Int): String =
    if (minutes >= 60) {
        stringResource(Res.string.bn_history_hours_minutes, minutes / 60, minutes % 60)
    } else {
        stringResource(Res.string.bn_history_minutes, minutes)
    }

@Composable
private fun WatchHistoryStatsBlock(
    stats: WatchHistoryStats,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WatchStatTile(
                title = stringResource(Res.string.bn_history_this_month),
                value = formatWatchMinutes(stats.minutesThisMonth),
                caption = stringResource(Res.string.bn_history_last_month, formatWatchMinutes(stats.minutesLastMonth)),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            WatchStatTile(
                title = stringResource(Res.string.bn_history_streak),
                value = stringResource(Res.string.bn_history_days, stats.streakDays),
                caption = stringResource(Res.string.bn_history_record, stats.recordDays),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            WatchStatTile(
                title = stringResource(Res.string.bn_history_finished),
                value = (stats.finishedMovies + stats.finishedEpisodes).toString(),
                caption = stringResource(Res.string.bn_history_finished_detail, stats.finishedMovies, stats.finishedEpisodes),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        MinutesPerDayChart(days = stats.days, today = today)
    }
}

@Composable
private fun WatchStatTile(
    title: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/**
 * Os 14 dias como barras finas de uma cor só (magnitude), cantos arredondados só na ponta
 * de cima e presos à base. Tocar numa barra mostra os minutos daquele dia no título.
 */
@Composable
private fun MinutesPerDayChart(days: List<WatchHistoryDay>, today: LocalDate) {
    if (days.isEmpty()) return
    var selected by remember(days) { mutableStateOf(days.lastIndex) }
    val maxMinutes = days.maxOf { it.minutes }.coerceAtLeast(1)
    val selectedDay = days.getOrNull(selected) ?: days.last()
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(Res.string.bn_history_minutes_per_day),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    Res.string.bn_history_day_minutes,
                    historyDayLabel(selectedDay.date, today),
                    formatWatchMinutes(selectedDay.minutes),
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEachIndexed { index, day ->
                    val fraction = day.minutes.toFloat() / maxMinutes
                    val label = "${historyDayLabel(day.date, today)}: ${formatWatchMinutes(day.minutes)}"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selected = index }
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .fillMaxHeight(if (day.minutes > 0) fraction.coerceAtLeast(0.03f) else 0.02f)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    when {
                                        day.minutes <= 0 -> trackColor
                                        index == selected -> barColor
                                        else -> barColor.copy(alpha = 0.55f)
                                    },
                                ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                days.forEach { day ->
                    Text(
                        text = day.date.toString().substring(8, 10),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchHistoryRow(
    feedEntry: WatchHistoryFeedEntry,
    onClick: () -> Unit,
) {
    val entry = feedEntry.entry
    val subtitle = when {
        entry.isEpisode && !entry.episodeTitle.isNullOrBlank() -> stringResource(
            Res.string.bn_history_episode_named,
            entry.seasonNumber ?: 0,
            entry.episodeNumber ?: 0,
            entry.episodeTitle.orEmpty(),
        )
        entry.isEpisode -> stringResource(Res.string.bn_history_episode, entry.seasonNumber ?: 0, entry.episodeNumber ?: 0)
        else -> stringResource(Res.string.bn_history_movie)
    }
    val time = feedEntry.time
    val clock = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = entry.poster ?: entry.background ?: entry.episodeThumbnail,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 44.dp, height = 66.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { feedEntry.percent / 100f },
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${feedEntry.percent}% · $clock",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
