package com.nuvio.app.features.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.ThemeColors
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.labelRes
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.bn_accent_description
import nuvio.composeapp.generated.resources.bn_accent_immersive
import nuvio.composeapp.generated.resources.bn_accent_more_colors
import nuvio.composeapp.generated.resources.bn_accent_preview_fixed
import nuvio.composeapp.generated.resources.bn_accent_preview_immersive
import nuvio.composeapp.generated.resources.bn_accent_preview_button
import nuvio.composeapp.generated.resources.cd_selected
import org.jetbrains.compose.resources.stringResource

// Better Nuvio: the TV's accent colours, in the TV's order (Imersiva first).
private val BetterNuvioFixedAccents = listOf(
    AppTheme.WHITE,
    AppTheme.CRIMSON,
    AppTheme.OCEAN,
    AppTheme.VIOLET,
    AppTheme.EMERALD,
    AppTheme.AMBER,
    AppTheme.ROSE,
)

// Stand-in for the TV's cor viva: a sweep of artwork-like hues.
private val ImmersiveBrush = Brush.sweepGradient(
    listOf(
        Color(0xFFE5484D),
        Color(0xFFF5A524),
        Color(0xFF30A46C),
        Color(0xFF3E8EF7),
        Color(0xFF8E4EC6),
        Color(0xFFE5484D),
    ),
)

/**
 * "Cor de destaque" from the approved Ajustes proposal (Iago, 22/10/2026).
 * Imersiva follows the title artwork; for now it rides on the Enhanced
 * dynamic artwork colour until the full cor viva lands.
 */
@Composable
internal fun BetterNuvioAccentPicker(
    isTablet: Boolean,
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val immersive by ThemeSettingsRepository.dynamicArtworkBackgroundEnabled.collectAsStateWithLifecycle()
    val customColors by ThemeSettingsRepository.customThemeColors.collectAsStateWithLifecycle()
    val palette = remember(selectedTheme, customColors) { ThemeColors.getColorPalette(selectedTheme, customColors) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    val horizontal = if (isTablet) NuvioTokens.Space.s20 else NuvioTokens.Space.s16
    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Text(
            text = stringResource(Res.string.bn_accent_description),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
            modifier = Modifier.padding(start = horizontal, end = horizontal, top = NuvioTokens.Space.s14),
        )
        val chips: List<AppTheme?> = listOf(null) + BetterNuvioFixedAccents
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontal, vertical = NuvioTokens.Space.s14),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        ) {
            chips.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                ) {
                    row.forEach { theme ->
                        val isSelected = if (theme == null) immersive else !immersive && theme == selectedTheme
                        AccentChip(
                            label = if (theme == null) stringResource(Res.string.bn_accent_immersive) else stringResource(theme.labelRes),
                            brush = if (theme == null) ImmersiveBrush else ThemeColors.getColorPalette(theme, customColors).accentBrush(),
                            isSelected = isSelected,
                            onClick = {
                                if (theme == null) {
                                    ThemeSettingsRepository.setDynamicArtworkBackground(true)
                                } else {
                                    ThemeSettingsRepository.setDynamicArtworkBackground(false)
                                    onThemeSelected(theme)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        // Live preview: the main button in the current accent.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = horizontal, end = horizontal, bottom = NuvioTokens.Space.s14),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.bn_accent_preview_button),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.onSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (immersive) ImmersiveBrush else palette.accentBrush())
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
            Spacer(Modifier.size(NuvioTokens.Space.s12))
            Text(
                text = stringResource(
                    if (immersive) Res.string.bn_accent_preview_immersive else Res.string.bn_accent_preview_fixed,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The Enhanced extra themes (Gold, Jade, custom…) stay one tap away.
        Text(
            text = stringResource(Res.string.bn_accent_more_colors),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = tokens.colors.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMore = !showMore }
                .padding(horizontal = horizontal, vertical = NuvioTokens.Space.s12),
        )
        if (showMore) {
            AppearanceThemePicker(
                isTablet = isTablet,
                selectedTheme = selectedTheme,
                onThemeSelected = { theme ->
                    ThemeSettingsRepository.setDynamicArtworkBackground(false)
                    onThemeSelected(theme)
                },
            )
        }
    }
}

@Composable
private fun AccentChip(
    label: String,
    brush: Brush,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier
            .clip(tokens.shapes.compactCard)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = NuvioTokens.Space.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(48.dp).then(
                if (isSelected) Modifier.border(2.dp, tokens.colors.textPrimary, CircleShape) else Modifier,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(brush),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(Res.string.cd_selected),
                        tint = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(NuvioTokens.Space.s6))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) tokens.colors.textPrimary else tokens.colors.textSecondary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
