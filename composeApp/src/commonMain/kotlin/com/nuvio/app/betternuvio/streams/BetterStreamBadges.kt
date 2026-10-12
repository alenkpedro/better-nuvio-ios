package com.nuvio.app.betternuvio.streams

import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

/** The TV app's monochrome white badge art (deploy/app/art/badges), by badge id. Generated. */
internal fun betterBadgeDrawable(id: String): DrawableResource? = when (id) {
    "r-4k" -> Res.drawable.bn_badge_r_4k
    "r-1080" -> Res.drawable.bn_badge_r_1080
    "r-720" -> Res.drawable.bn_badge_r_720
    "q-remux" -> Res.drawable.bn_badge_q_remux
    "q-bluray" -> Res.drawable.bn_badge_q_bluray
    "q-webdl" -> Res.drawable.bn_badge_q_webdl
    "q-webrip" -> Res.drawable.bn_badge_q_webrip
    "q-seadex" -> Res.drawable.bn_badge_q_seadex
    "v-dv" -> Res.drawable.bn_badge_v_dv
    "v-hdr10plus" -> Res.drawable.bn_badge_v_hdr10plus
    "v-hdr10" -> Res.drawable.bn_badge_v_hdr10
    "v-hdr" -> Res.drawable.bn_badge_v_hdr
    "v-imax-enhanced" -> Res.drawable.bn_badge_v_imax_enhanced
    "v-imax" -> Res.drawable.bn_badge_v_imax
    "v-sdr" -> Res.drawable.bn_badge_v_sdr
    "a-atmos-dv" -> Res.drawable.bn_badge_a_atmos_dv
    "a-atmos" -> Res.drawable.bn_badge_a_atmos
    "a-truehd-dv" -> Res.drawable.bn_badge_a_truehd_dv
    "a-truehd" -> Res.drawable.bn_badge_a_truehd
    "a-dtsx" -> Res.drawable.bn_badge_a_dtsx
    "a-dtshdma" -> Res.drawable.bn_badge_a_dtshdma
    "a-dtshd" -> Res.drawable.bn_badge_a_dtshd
    "a-dts" -> Res.drawable.bn_badge_a_dts
    "a-dd-dv" -> Res.drawable.bn_badge_a_dd_dv
    "a-ddp" -> Res.drawable.bn_badge_a_ddp
    "a-dd" -> Res.drawable.bn_badge_a_dd
    "c-71" -> Res.drawable.bn_badge_c_71
    "c-51" -> Res.drawable.bn_badge_c_51
    "co-x265" -> Res.drawable.bn_badge_co_x265
    "co-x264" -> Res.drawable.bn_badge_co_x264
    "co-av1" -> Res.drawable.bn_badge_co_av1
    "p-netflix" -> Res.drawable.bn_badge_p_netflix
    "p-prime" -> Res.drawable.bn_badge_p_prime
    "p-appletv" -> Res.drawable.bn_badge_p_appletv
    "p-disney" -> Res.drawable.bn_badge_p_disney
    "p-max" -> Res.drawable.bn_badge_p_max
    "p-hulu" -> Res.drawable.bn_badge_p_hulu
    "p-peacock" -> Res.drawable.bn_badge_p_peacock
    "p-paramount" -> Res.drawable.bn_badge_p_paramount
    "p-crave" -> Res.drawable.bn_badge_p_crave
    "p-crunchyroll" -> Res.drawable.bn_badge_p_crunchyroll
    else -> null
}
