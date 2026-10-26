package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.corViva
import com.nuvio.app.core.ui.nuvioTypeScale
import com.nuvio.app.core.ui.onCorViva
import com.nuvio.app.features.livetv.formatLiveTvEpgTime
import com.nuvio.app.features.livetv.rememberLiveTvEpgNowMillis
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Better Nuvio: um item do menu "Mais" do player (título + linha de apoio). */
internal data class PlayerMoreAction(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
    val icon: ImageVector,
    val highlighted: Boolean = false,
)

/** Relógio do canto do player, como na TV. Atualiza a cada meio minuto. */
@Composable
internal fun PlayerClock(modifier: Modifier = Modifier) {
    val nowMillis = rememberLiveTvEpgNowMillis()
    Text(
        text = formatLiveTvEpgTime(nowMillis),
        style = MaterialTheme.nuvioTypeScale.bodyMd.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        color = Color.White.copy(alpha = 0.92f),
        maxLines = 1,
        modifier = modifier,
    )
}

/** Botão "Mais" na cor viva, que abre o menu com as ações menos usadas. */
@Composable
internal fun PlayerMoreButton(
    actions: List<PlayerMoreAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    val color = corViva()
    val label = stringResource(Res.string.bn_player_more)
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(role = Role.Button, onClickLabel = label) {
                    onExpandedChange(true)
                    onInteraction()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = label, tint = color.onCorViva(), modifier = Modifier.size(24.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xF018181C),
            modifier = Modifier.widthIn(min = 260.dp),
        ) {
            actions.forEach { action ->
                PlayerMoreRow(
                    action = action,
                    onClick = {
                        onExpandedChange(false)
                        onInteraction()
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerMoreRow(action: PlayerMoreAction, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = if (action.highlighted) corViva() else Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = action.title,
                style = MaterialTheme.nuvioTypeScale.bodyMd.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = action.subtitle,
                style = MaterialTheme.nuvioTypeScale.labelSm.copy(fontSize = 12.sp),
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Monta o menu Mais com o que estiver disponível, na ordem da TV. */
@Composable
internal fun playerMoreActions(
    onRateClick: (() -> Unit)?,
    userRating: Int?,
    onPartyClick: (() -> Unit)?,
    partyActive: Boolean,
    onOpenInExternalPlayer: (() -> Unit)?,
    onSwitchEngineClick: (() -> Unit)?,
    onVideoSettingsClick: (() -> Unit)?,
    onInfoClick: (() -> Unit)?,
    onStreamInfoClick: (() -> Unit)?,
    onSubmitIntroClick: (() -> Unit)?,
): List<PlayerMoreAction> = listOfNotNull(
    onRateClick?.let {
        PlayerMoreAction(
            title = stringResource(Res.string.bn_more_rate),
            subtitle = userRating?.let { rating -> stringResource(Res.string.user_rating_action_rated, rating) }
                ?: stringResource(Res.string.bn_more_rate_sub),
            onClick = it,
            icon = if (userRating != null) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            highlighted = userRating != null,
        )
    },
    onPartyClick?.let {
        PlayerMoreAction(
            title = stringResource(Res.string.bn_more_party),
            subtitle = stringResource(Res.string.bn_more_party_sub),
            onClick = it,
            icon = Icons.Rounded.Groups,
            highlighted = partyActive,
        )
    },
    onOpenInExternalPlayer?.let {
        PlayerMoreAction(
            title = stringResource(Res.string.bn_more_external),
            subtitle = stringResource(Res.string.bn_more_external_sub),
            onClick = it,
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
        )
    },
    onSwitchEngineClick?.let {
        PlayerMoreAction(
            title = stringResource(Res.string.bn_more_engine),
            subtitle = stringResource(Res.string.bn_more_engine_sub),
            onClick = it,
            icon = Icons.Rounded.SwapHoriz,
        )
    },
    onVideoSettingsClick?.let {
        PlayerMoreAction(
            title = stringResource(Res.string.bn_more_video),
            subtitle = stringResource(Res.string.bn_more_video_sub),
            onClick = it,
            icon = Icons.Rounded.Build,
        )
    },
    onInfoClick?.let {
        PlayerMoreAction(
            title = stringResource(Res.string.bn_more_info),
            subtitle = stringResource(Res.string.bn_more_info_sub),
            onClick = it,
            icon = Icons.Rounded.Info,
        )
    },
    onStreamInfoClick?.let {
        PlayerMoreAction(
            title = stringResource(Res.string.cd_stream_info),
            subtitle = stringResource(Res.string.bn_more_source_info_sub),
            onClick = it,
            icon = Icons.Rounded.Dns,
        )
    },
    onSubmitIntroClick?.let {
        PlayerMoreAction(
            title = stringResource(Res.string.bn_more_intro),
            subtitle = stringResource(Res.string.bn_more_intro_sub),
            onClick = it,
            icon = Icons.Rounded.Flag,
        )
    },
)
