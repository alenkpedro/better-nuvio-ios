package com.nuvio.app.features.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.queue.QueueItem
import com.nuvio.app.features.queue.QueueProfileState
import com.nuvio.app.features.queue.QueueRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.bn_queue_binge_sub
import nuvio.composeapp.generated.resources.bn_queue_binge_title
import nuvio.composeapp.generated.resources.bn_queue_clear
import nuvio.composeapp.generated.resources.bn_queue_empty_message
import nuvio.composeapp.generated.resources.bn_queue_empty_title
import nuvio.composeapp.generated.resources.bn_queue_move_down
import nuvio.composeapp.generated.resources.bn_queue_move_up
import nuvio.composeapp.generated.resources.bn_queue_remove
import org.jetbrains.compose.resources.stringResource

/**
 * Better Nuvio: a aba Fila da Biblioteca. Mesmo conteúdo do painel "A seguir" da TV
 * (aseguir.c): a chave do modo maratona em cima, a fila na ordem, e "Limpar fila" no fim.
 * Aqui dá também para reordenar, que a TV não faz.
 */
internal fun LazyListScope.libraryQueueContent(
    queue: QueueProfileState,
    onItemClick: (QueueItem) -> Unit,
) {
    item(key = "queue-binge") {
        QueueBingeModeRow(
            enabled = queue.bingeMode,
            onToggle = QueueRepository::setBingeMode,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
    if (queue.items.isEmpty()) {
        item(key = "queue-empty") {
            HomeEmptyStateCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                title = stringResource(Res.string.bn_queue_empty_title),
                message = stringResource(Res.string.bn_queue_empty_message),
            )
        }
        return
    }
    itemsIndexed(queue.items, key = { _, item -> "queue-${item.type}-${item.id}" }) { index, item ->
        QueueItemRow(
            item = item,
            position = index + 1,
            canMoveUp = index > 0,
            canMoveDown = index < queue.items.lastIndex,
            onClick = { onItemClick(item) },
            onMoveUp = { QueueRepository.move(index, index - 1) },
            onMoveDown = { QueueRepository.move(index, index + 1) },
            onRemove = { QueueRepository.remove(item.id) },
            modifier = Modifier.animateItem(),
        )
    }
    item(key = "queue-clear") {
        TextButton(
            onClick = QueueRepository::clear,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.bn_queue_clear),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun QueueBingeModeRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onToggle(!enabled) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.bn_queue_binge_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.bn_queue_binge_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun QueueItemRow(
    item: QueueItem,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(22.dp),
            )
            AsyncImage(
                model = item.poster ?: item.banner,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 48.dp, height = 72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.releaseInfo?.takeIf { it.isNotBlank() }?.let { info ->
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(Res.string.bn_queue_move_up))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(Res.string.bn_queue_move_down))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.bn_queue_remove))
            }
        }
    }
}
