package com.nuvio.app.features.settings

import com.nuvio.app.features.player.BuiltInSubtitleFont
import com.nuvio.app.features.player.previewFamily
import nuvio.composeapp.generated.resources.bn_subtitle_font_default_tag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.player.SubtitleFontRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Picks the subtitle font (Better Nuvio: the TV's 7 fonts; no import, fonts imported before can still be deleted). */
@Composable
internal fun SubtitleFontDialog(
    selectedFontFile: String?,
    onFontSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { SubtitleFontRepository.ensureLoaded() } }
    val fonts by SubtitleFontRepository.fonts.collectAsState()
    val selected = selectedFontFile?.takeIf { file ->
        BuiltInSubtitleFont.fromId(file) != null || fonts.any { it.fileName == file }
    } ?: BuiltInSubtitleFont.DEFAULT.id

    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.settings_playback_subtitle_font),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Better Nuvio: the TV's 7 fonts first, each sample written in its own face.
            BuiltInSubtitleFont.entries.forEach { font ->
                DialogOption(
                    text = font.label,
                    description = if (font == BuiltInSubtitleFont.DEFAULT) {
                        stringResource(Res.string.bn_subtitle_font_default_tag)
                    } else {
                        null
                    },
                    selected = selected == font.id,
                    onClick = { onFontSelected(font.id) },
                    leading = {
                        Text(
                            text = "Aa",
                            fontFamily = font.previewFamily(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.nuvio.colors.textPrimary,
                        )
                    },
                )
            }
            fonts.forEach { font ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DialogOption(
                        text = font.familyName,
                        description = font.fileName,
                        selected = selected == font.fileName,
                        onClick = { onFontSelected(font.fileName) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { SubtitleFontRepository.delete(font) }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(
                                Res.string.settings_playback_subtitle_font_delete,
                                font.familyName,
                            ),
                            tint = MaterialTheme.nuvio.colors.textSecondary,
                        )
                    }
                }
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                style = DialogButtonStyle.Primary,
                onClick = onDismiss,
            )
        }
    }
}
