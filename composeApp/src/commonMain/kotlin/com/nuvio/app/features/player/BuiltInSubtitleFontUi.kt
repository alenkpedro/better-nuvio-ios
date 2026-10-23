package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.bn_sub_atkinson
import nuvio.composeapp.generated.resources.bn_sub_dm_sans
import nuvio.composeapp.generated.resources.bn_sub_inter
import nuvio.composeapp.generated.resources.bn_sub_montserrat
import nuvio.composeapp.generated.resources.bn_sub_netflix_sans
import nuvio.composeapp.generated.resources.bn_sub_open_sans
import nuvio.composeapp.generated.resources.bn_sub_roboto
import org.jetbrains.compose.resources.Font

/** The font itself, so each option can be written in its own face (as on the TV). */
@Composable
internal fun BuiltInSubtitleFont.previewFamily(): FontFamily = FontFamily(
    Font(
        when (this) {
            BuiltInSubtitleFont.NETFLIX_SANS -> Res.font.bn_sub_netflix_sans
            BuiltInSubtitleFont.INTER -> Res.font.bn_sub_inter
            BuiltInSubtitleFont.DM_SANS -> Res.font.bn_sub_dm_sans
            BuiltInSubtitleFont.OPEN_SANS -> Res.font.bn_sub_open_sans
            BuiltInSubtitleFont.ROBOTO -> Res.font.bn_sub_roboto
            BuiltInSubtitleFont.MONTSERRAT -> Res.font.bn_sub_montserrat
            BuiltInSubtitleFont.ATKINSON -> Res.font.bn_sub_atkinson
        },
    ),
)
