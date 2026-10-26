package com.nuvio.app.features.settings

import androidx.compose.foundation.border
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.nuvio.app.core.sync.SyncManager
import nuvio.composeapp.generated.resources.bn_settings_switch_profile
import nuvio.composeapp.generated.resources.bn_settings_synced_now
import nuvio.composeapp.generated.resources.bn_settings_synced_min
import nuvio.composeapp.generated.resources.bn_settings_synced_hours
import nuvio.composeapp.generated.resources.bn_settings_not_synced
import nuvio.composeapp.generated.resources.bn_settings_page_look
import nuvio.composeapp.generated.resources.bn_settings_page_notices
import nuvio.composeapp.generated.resources.bn_settings_notices_description
import nuvio.composeapp.generated.resources.bn_settings_section_app
import nuvio.composeapp.generated.resources.bn_settings_profiles
import nuvio.composeapp.generated.resources.bn_settings_trakt_simkl
import nuvio.composeapp.generated.resources.bn_settings_content
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.AppLoadingProfileAvatar
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.features.profiles.ProfileRepository
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.nuvio.app.core.build.AppVersionConfig
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.bn_about_fork_by
import nuvio.composeapp.generated.resources.compose_about_made_with
import nuvio.composeapp.generated.resources.compose_about_version_format
import nuvio.composeapp.generated.resources.compose_settings_page_account
import nuvio.composeapp.generated.resources.compose_settings_page_advanced
import nuvio.composeapp.generated.resources.compose_settings_page_appearance
import nuvio.composeapp.generated.resources.compose_settings_page_integrations
import nuvio.composeapp.generated.resources.compose_settings_page_licenses_attributions
import nuvio.composeapp.generated.resources.compose_settings_page_notifications
import nuvio.composeapp.generated.resources.compose_settings_page_playback
import nuvio.composeapp.generated.resources.compose_settings_page_privacy_policy
import nuvio.composeapp.generated.resources.compose_settings_page_supporters_contributors
import nuvio.composeapp.generated.resources.compose_settings_root_account_description
import nuvio.composeapp.generated.resources.compose_settings_root_appearance_description
import nuvio.composeapp.generated.resources.compose_settings_root_check_updates_description
import nuvio.composeapp.generated.resources.compose_settings_root_check_updates_title
import nuvio.composeapp.generated.resources.compose_settings_root_content_discovery_description
import nuvio.composeapp.generated.resources.compose_settings_root_general_section
import nuvio.composeapp.generated.resources.compose_settings_root_integrations_description
import nuvio.composeapp.generated.resources.compose_settings_root_notifications_description
import nuvio.composeapp.generated.resources.compose_settings_root_profile_description
import nuvio.composeapp.generated.resources.compose_settings_root_profile_title
import nuvio.composeapp.generated.resources.compose_settings_root_privacy_policy_description
import nuvio.composeapp.generated.resources.compose_settings_root_switch_profile_description
import nuvio.composeapp.generated.resources.compose_settings_root_switch_profile_title
import nuvio.composeapp.generated.resources.compose_settings_root_tracking_description
import nuvio.composeapp.generated.resources.compose_settings_root_about_section
import nuvio.composeapp.generated.resources.compose_settings_root_account_section
import nuvio.composeapp.generated.resources.compose_settings_root_advanced_description
import nuvio.composeapp.generated.resources.compose_settings_root_advanced_section
import nuvio.composeapp.generated.resources.compose_settings_page_content_discovery
import nuvio.composeapp.generated.resources.compose_settings_page_tracking
import nuvio.composeapp.generated.resources.compose_settings_page_whats_new
import nuvio.composeapp.generated.resources.settings_playback_subtitle
import nuvio.composeapp.generated.resources.updates_debug_test_description
import nuvio.composeapp.generated.resources.updates_debug_test_title
import nuvio.composeapp.generated.resources.party_settings_description
import nuvio.composeapp.generated.resources.party_title
import nuvio.composeapp.generated.resources.about_supporters_contributors_subtitle
import nuvio.composeapp.generated.resources.about_licenses_attributions_subtitle
import nuvio.composeapp.generated.resources.whats_new_root_description
import org.jetbrains.compose.resources.stringResource
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository
import com.nuvio.app.features.simkl.SimklAuthRepository
import com.nuvio.app.features.trakt.TraktAuthRepository
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.core.ui.labelRes
import nuvio.composeapp.generated.resources.bn_accent_immersive
import nuvio.composeapp.generated.resources.bn_settings_watch_together
import nuvio.composeapp.generated.resources.bn_settings_profiles_count
import nuvio.composeapp.generated.resources.bn_settings_progress_count
import nuvio.composeapp.generated.resources.bn_settings_in_list
import nuvio.composeapp.generated.resources.bn_settings_profile_stats
import nuvio.composeapp.generated.resources.bn_settings_notifications_on
import nuvio.composeapp.generated.resources.bn_settings_notifications_off
import nuvio.composeapp.generated.resources.bn_streams_addons_count
import org.jetbrains.compose.resources.pluralStringResource

private const val PRIVACY_POLICY_URL = "https://nuvio.tv/privacy-policy"

internal fun LazyListScope.settingsRootContent(
    isTablet: Boolean,
    onPlaybackClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onAdvancedClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onContentDiscoveryClick: () -> Unit,
    onIntegrationsClick: () -> Unit,
    onTrackingClick: () -> Unit,
    onSupportersContributorsClick: () -> Unit,
    onLicensesAttributionsClick: () -> Unit,
    onWhatsNewClick: () -> Unit,
    onCheckForUpdatesClick: (() -> Unit)? = null,
    onTestUpdateBannerClick: (() -> Unit)? = null,
    onAccountClick: () -> Unit,
    onSwitchProfileClick: (() -> Unit)? = null,
    onWatchPartyClick: (() -> Unit)? = null,
    onLookClick: () -> Unit = onAppearanceClick,
    onNoticesClick: (() -> Unit)? = null,
    onSwitchProfileNow: (() -> Unit)? = null,
    showAccountSection: Boolean = true,
    showGeneralSection: Boolean = true,
    showAboutSection: Boolean = true,
    showAdvancedSection: Boolean = true,
    showSupportersContributorsPage: Boolean = true,
) {
    // Better Nuvio layout (spec dos Ajustes, 22/10/2026): cartão do perfil, depois Conta, App e Sobre.
    // Como na TV, o cartão abre a Conta; "Perfis" abre a página do perfil e "Trocar perfil" a escolha.
    if (showAccountSection) {
        item {
            SettingsProfileCard(
                isTablet = isTablet,
                onClick = onAccountClick,
                onSwitchProfile = onSwitchProfileNow,
            )
        }
    }
    if (showAccountSection) {
        item {
            val profileCount = ProfileRepository.state.collectAsStateWithLifecycle().value.profiles.size
            val traktConnected by TraktAuthRepository.isAuthenticated.collectAsStateWithLifecycle()
            val simklConnected by SimklAuthRepository.isAuthenticated.collectAsStateWithLifecycle()
            SettingsSection(
                title = stringResource(Res.string.compose_settings_root_account_section),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    val profilesClick = onSwitchProfileClick ?: onSwitchProfileNow
                    if (profilesClick != null) {
                        SettingsNavigationRow(
                            title = stringResource(Res.string.bn_settings_profiles),
                            description = null,
                            icon = Icons.Rounded.People,
                            isTablet = isTablet,
                            value = if (profileCount > 0) {
                                pluralStringResource(Res.plurals.bn_settings_profiles_count, profileCount, profileCount)
                            } else {
                                null
                            },
                            showChevron = true,
                            onClick = profilesClick,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                    }
                    SettingsNavigationRow(
                        title = stringResource(Res.string.bn_settings_trakt_simkl),
                        description = null,
                        icon = Icons.Default.Sync,
                        isTablet = isTablet,
                        value = when {
                            traktConnected && simklConnected -> "Trakt · Simkl"
                            traktConnected -> "Trakt"
                            simklConnected -> "Simkl"
                            else -> null
                        },
                        showChevron = true,
                        onClick = onTrackingClick,
                    )
                    if (onWatchPartyClick != null) {
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.bn_settings_watch_together),
                            description = null,
                            icon = Icons.Rounded.Groups,
                            isTablet = isTablet,
                            showChevron = true,
                            onClick = onWatchPartyClick,
                        )
                    }
                }
            }
        }
    }
    val advancedInsideApp = showGeneralSection && showAdvancedSection
    if (showGeneralSection) {
        item {
            val immersive by ThemeSettingsRepository.dynamicArtworkBackgroundEnabled.collectAsStateWithLifecycle()
            val selectedTheme by ThemeSettingsRepository.selectedTheme.collectAsStateWithLifecycle()
            val addonCount = AddonRepository.uiState.collectAsStateWithLifecycle().value.addons.size
            val notificationsOn = EpisodeReleaseNotificationsRepository.uiState.collectAsStateWithLifecycle().value.isEnabled
            SettingsSection(
                title = stringResource(Res.string.bn_settings_section_app),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsNavigationRow(
                        title = stringResource(Res.string.bn_settings_page_look),
                        description = null,
                        icon = Icons.Rounded.Palette,
                        isTablet = isTablet,
                        value = if (immersive) stringResource(Res.string.bn_accent_immersive) else stringResource(selectedTheme.labelRes),
                        showChevron = true,
                        onClick = onLookClick,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_appearance),
                        description = null,
                        icon = Icons.Rounded.Dashboard,
                        isTablet = isTablet,
                        showChevron = true,
                        onClick = onAppearanceClick,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.bn_settings_content),
                        description = null,
                        icon = Icons.Rounded.Extension,
                        isTablet = isTablet,
                        value = if (addonCount > 0) pluralStringResource(Res.plurals.bn_streams_addons_count, addonCount, addonCount) else null,
                        showChevron = true,
                        onClick = onContentDiscoveryClick,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_playback),
                        description = null,
                        icon = Icons.Rounded.PlayArrow,
                        isTablet = isTablet,
                        showChevron = true,
                        onClick = onPlaybackClick,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_integrations),
                        description = null,
                        icon = Icons.Rounded.Link,
                        isTablet = isTablet,
                        showChevron = true,
                        onClick = onIntegrationsClick,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_notifications),
                        description = null,
                        icon = Icons.Rounded.Notifications,
                        isTablet = isTablet,
                        value = stringResource(if (notificationsOn) Res.string.bn_settings_notifications_on else Res.string.bn_settings_notifications_off),
                        showChevron = true,
                        onClick = onNotificationsClick,
                    )
                    if (advancedInsideApp) {
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.compose_settings_page_advanced),
                            description = null,
                            icon = Icons.Rounded.Tune,
                            isTablet = isTablet,
                            showChevron = true,
                            onClick = onAdvancedClick,
                        )
                    }
                }
            }
        }
    }
    if (showAboutSection) {
        item {
            val uriHandler = LocalUriHandler.current
            SettingsSection(
                title = stringResource(Res.string.compose_settings_root_about_section),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    if (showSupportersContributorsPage) {
                        SettingsNavigationRow(
                            title = stringResource(Res.string.compose_settings_page_supporters_contributors),
                            description = stringResource(Res.string.about_supporters_contributors_subtitle),
                            icon = Icons.Rounded.Favorite,
                            isTablet = isTablet,
                            onClick = onSupportersContributorsClick,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                    }
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_whats_new),
                        description = stringResource(Res.string.whats_new_root_description),
                        icon = Icons.Rounded.NewReleases,
                        isTablet = isTablet,
                        onClick = onWhatsNewClick,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    if (onNoticesClick != null) {
                        SettingsNavigationRow(
                            title = stringResource(Res.string.bn_settings_page_notices),
                            description = stringResource(Res.string.bn_settings_notices_description),
                            icon = Icons.Rounded.Campaign,
                            isTablet = isTablet,
                            onClick = onNoticesClick,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                    }
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_privacy_policy),
                        description = stringResource(Res.string.compose_settings_root_privacy_policy_description),
                        icon = Icons.Rounded.Policy,
                        isTablet = isTablet,
                        onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_licenses_attributions),
                        description = stringResource(Res.string.about_licenses_attributions_subtitle),
                        icon = Icons.Rounded.Info,
                        isTablet = isTablet,
                        onClick = onLicensesAttributionsClick,
                    )
                    if (onCheckForUpdatesClick != null) {
                        SettingsGroupDivider(isTablet = isTablet)
                        UpdateChannelSettingsRow(isTablet = isTablet)
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.compose_settings_root_check_updates_title),
                            description = stringResource(Res.string.compose_settings_root_check_updates_description),
                            icon = Icons.Rounded.CloudDownload,
                            isTablet = isTablet,
                            onClick = onCheckForUpdatesClick,
                        )
                    }
                    if (onTestUpdateBannerClick != null) {
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.updates_debug_test_title),
                            description = stringResource(Res.string.updates_debug_test_description),
                            icon = Icons.Rounded.BugReport,
                            isTablet = isTablet,
                            onClick = onTestUpdateBannerClick,
                        )
                    }
                }
            }
        }
    }
    if (showAdvancedSection && !advancedInsideApp) {
        item {
            SettingsSection(
                title = stringResource(Res.string.compose_settings_root_advanced_section),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_advanced),
                        description = stringResource(Res.string.compose_settings_root_advanced_description),
                        icon = Icons.Rounded.Tune,
                        isTablet = isTablet,
                        onClick = onAdvancedClick,
                    )
                }
            }
        }
    }
    item {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = if (isTablet) 20.dp else 16.dp),
        ) {
            if (showAboutSection) {
                MemberBrandWordmark(
                    height = if (isTablet) 30.dp else 26.dp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.height(if (isTablet) 10.dp else 8.dp),
                )
            }
            Text(
                text = stringResource(Res.string.compose_about_made_with),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.bn_about_fork_by),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    Res.string.compose_about_version_format,
                    AppVersionConfig.VERSION_NAME,
                    AppVersionConfig.VERSION_CODE,
                ),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SettingsProfileCard(
    isTablet: Boolean,
    onClick: () -> Unit,
    onSwitchProfile: (() -> Unit)? = null,
) {
    val lastSyncedAt by SyncManager.lastSyncedAtEpochMs.collectAsStateWithLifecycle()
    val tokens = MaterialTheme.nuvio
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val profile = profileState.activeProfile
    val avatarSize = if (isTablet) 64.dp else 52.dp
    SettingsGroup(
        isTablet = isTablet,
        modifier = Modifier.padding(top = if (isTablet) 12.dp else 8.dp),
    ) {
        // Prévia dos Ajustes: avatar, nome e números da conta em cima; sincronia e "Trocar perfil" embaixo,
        // sobre um leve tom da cor viva.
        val addonCount = AddonRepository.uiState.collectAsStateWithLifecycle().value.addons.size
        val progressCount = WatchProgressRepository.uiState.collectAsStateWithLifecycle().value.entries.size
        val listCount = LibraryRepository.uiState.collectAsStateWithLifecycle().value.items.size
        val horizontal = if (isTablet) 20.dp else 16.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(tokens.colors.accent.copy(alpha = 0.16f), Color.Transparent),
                    ),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = horizontal, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .border(2.dp, MaterialTheme.themePalette.accentBrush(), CircleShape)
                        .padding(3.dp),
                ) {
                    if (profile != null) {
                        AppLoadingProfileAvatar(profile = profile, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.People,
                            contentDescription = null,
                            tint = tokens.colors.accent,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile?.name?.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.compose_settings_root_profile_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            Res.string.bn_settings_profile_stats,
                            pluralStringResource(Res.plurals.bn_streams_addons_count, addonCount, addonCount),
                            pluralStringResource(Res.plurals.bn_settings_progress_count, progressCount, progressCount),
                            stringResource(Res.string.bn_settings_in_list, listCount),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (lastSyncedAt != null) Color(0xFF34C759) else tokens.colors.textMuted),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = syncStatusText(lastSyncedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (onSwitchProfile != null) {
                    Text(
                        text = stringResource(Res.string.bn_settings_switch_profile),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.colors.textPrimary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .clickable(onClick = onSwitchProfile)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun syncStatusText(lastSyncedAt: Long?): String {
    if (lastSyncedAt == null) return stringResource(Res.string.bn_settings_not_synced)
    // Recompute once a minute so "há N min" stays true while the page is open.
    var now by remember { mutableLongStateOf(EpisodeReleaseDatePlatform.nowEpochMs()) }
    LaunchedEffect(lastSyncedAt) {
        while (true) {
            now = EpisodeReleaseDatePlatform.nowEpochMs()
            delay(60_000)
        }
    }
    val minutes = ((now - lastSyncedAt) / 60_000L).coerceAtLeast(0L).toInt()
    return when {
        minutes < 1 -> stringResource(Res.string.bn_settings_synced_now)
        minutes < 60 -> stringResource(Res.string.bn_settings_synced_min, minutes)
        else -> stringResource(Res.string.bn_settings_synced_hours, minutes / 60)
    }
}
