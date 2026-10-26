package com.nuvio.app.features.queue

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.PosterZoomOverlayAction
import com.nuvio.app.features.home.MetaPreview
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.bn_queue_add
import nuvio.composeapp.generated.resources.bn_queue_remove
import org.jetbrains.compose.resources.stringResource

/**
 * Better Nuvio: "Adicionar à fila" no menu de segurar o pôster, como o ctxmenu.c da TV.
 */
@Composable
fun queuePosterOverlayAction(preview: MetaPreview): PosterZoomOverlayAction {
    val queueState by remember {
        QueueRepository.ensureLoaded()
        QueueRepository.uiState
    }.collectAsStateWithLifecycle()
    val base = QueueRepository.baseId(preview.id)
    val queued = queueState.items.any { it.id == base }
    return PosterZoomOverlayAction(
        icon = if (queued) Icons.Rounded.PlaylistRemove else Icons.AutoMirrored.Rounded.PlaylistAdd,
        label = stringResource(if (queued) Res.string.bn_queue_remove else Res.string.bn_queue_add),
        onSelected = { QueueRepository.toggle(preview.toQueueItem()) },
    )
}
