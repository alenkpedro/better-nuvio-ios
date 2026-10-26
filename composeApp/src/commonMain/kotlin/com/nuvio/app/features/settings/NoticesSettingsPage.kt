package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.features.notices.AppNotice
import com.nuvio.app.features.notices.NoticesRepository
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Better Nuvio: Ajustes > Avisos, a lista do canal do dono (o painel de avisos da TV). */
internal fun LazyListScope.noticesSettingsContent(
    isTablet: Boolean,
) {
    item {
        NoticesBody(isTablet = isTablet)
    }
}

@Composable
private fun NoticesBody(isTablet: Boolean) {
    val uiState by NoticesRepository.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        NoticesRepository.refresh(portuguese = getString(Res.string.bn_notices_lang) == "pt")
    }
    // Como na TV: abrir a lista marca tudo como lido (avisos_marcar_lidos), ao sair.
    DisposableEffect(Unit) {
        onDispose { NoticesRepository.markAllSeen() }
    }

    when {
        uiState.notices.isEmpty() && (uiState.isLoading || !uiState.loadedOnce) -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NuvioLoadingIndicator()
            }
        }

        uiState.notices.isEmpty() -> {
            SettingsGroup(isTablet = isTablet) {
                Text(
                    text = stringResource(Res.string.bn_notices_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        else -> {
            SettingsGroup(isTablet = isTablet) {
                uiState.notices.forEachIndexed { index, notice ->
                    NoticeRow(notice = notice)
                    if (index != uiState.notices.lastIndex) {
                        SettingsGroupDivider(isTablet = isTablet)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(notice: AppNotice) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!notice.seen) {
                Text(
                    text = stringResource(Res.string.bn_notices_new_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            notice.since?.let { since ->
                Text(
                    text = since,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = notice.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        if (notice.text.isNotBlank()) {
            Text(
                text = notice.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
