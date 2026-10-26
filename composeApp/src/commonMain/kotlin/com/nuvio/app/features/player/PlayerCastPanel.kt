package com.nuvio.app.features.player

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.MetaPerson
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Better Nuvio: o elenco do título a um toque do player (elencopl.c na TV). Não é um X-Ray:
 * nenhuma fonte aberta tem elenco por cena, então é o elenco do título com foto e
 * personagem, vindo dos metadados já carregados. Tocar numa pessoa só fecha o painel.
 */
@Composable
internal fun PlayerCastPanel(
    visible: Boolean,
    title: String,
    cast: List<MetaPerson>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio

    PlayerSidePanel(
        visible = visible,
        onDismiss = onDismiss,
        width = 420.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            PlayerPanelHeader(
                title = stringResource(Res.string.settings_meta_cast),
            ) {
                PlayerDialogButton(
                    label = stringResource(Res.string.action_close),
                    onClick = onDismiss,
                )
            }
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = tokens.colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (cast.isEmpty()) {
                Text(
                    text = stringResource(Res.string.bn_cast_panel_empty),
                    color = tokens.colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                ) {
                    itemsIndexed(
                        items = cast,
                        key = { index, person -> "${index}_${person.name}" },
                    ) { _, person ->
                        PlayerCastRow(person = person, onClick = onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerCastRow(
    person: MetaPerson,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapes.compactCard)
            .background(tokens.colors.surfacePopover)
            .border(tokens.borders.thin, tokens.colors.borderSubtle, tokens.shapes.compactCard)
            .clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(tokens.colors.surfaceCard),
            contentAlignment = Alignment.Center,
        ) {
            // Sem foto: a inicial sobre o cinza do cartão vazio, como na TV.
            Text(
                text = person.name.trim().take(1).uppercase(),
                color = tokens.colors.textSecondary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!person.photo.isNullOrBlank()) {
                AsyncImage(
                    model = person.photo,
                    contentDescription = person.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(modifier = Modifier.width(NuvioTokens.Space.s14))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = person.name,
                color = tokens.colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            person.role?.takeIf { it.isNotBlank() }?.let { role ->
                Text(
                    text = role,
                    color = tokens.colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
