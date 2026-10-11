package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nuvio.app.betternuvio.BetterNuvioPrefs
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.better_nuvio_autosync_description
import nuvio.composeapp.generated.resources.better_nuvio_autosync_title
import nuvio.composeapp.generated.resources.settings_playback_section_subtitle_audio
import org.jetbrains.compose.resources.stringResource

/** Better Nuvio rows in Playback settings (kept in one file to keep upstream merges small). */
@Composable
internal fun BetterNuvioSubtitleSettings(isTablet: Boolean) {
    var autoSync by remember { mutableStateOf(BetterNuvioPrefs.subtitleAutoSync) }
    SettingsSection(
        title = stringResource(Res.string.settings_playback_section_subtitle_audio) + " · Better Nuvio",
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.better_nuvio_autosync_title),
                description = stringResource(Res.string.better_nuvio_autosync_description),
                checked = autoSync,
                isTablet = isTablet,
                onCheckedChange = {
                    autoSync = it
                    BetterNuvioPrefs.subtitleAutoSync = it
                },
            )
        }
    }
}
