package com.nuvio.app.features.streams

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.bn_debrid_inactive_badge
import org.jetbrains.compose.resources.stringResource
import com.nuvio.app.core.ui.SkeletonBlock
import com.nuvio.app.features.debrid.DebridProviders
import androidx.compose.ui.graphics.compositeOver
import com.nuvio.app.core.ui.glass.ambientLiquidGlass
import com.nuvio.app.core.ui.glass.isAmbientLiquidGlassActive
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow

internal val LocalStreamHighlight = staticCompositionLocalOf<(StreamItem) -> String?> { { null } }

internal data class SharedStream(
    val label: String,
    val stream: StreamItem,
)

internal val LocalSharedStream = staticCompositionLocalOf<SharedStream?> { null }

@Composable
internal fun StreamCard(
    stream: StreamItem,
    enabled: Boolean,
    appendInstantServiceToDefaultName: Boolean,
    showFileSizeBadges: Boolean,
    showAddonLogo: Boolean,
    badgePlacement: StreamBadgePlacement,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    currentLabel: String? = null,
    outlined: Boolean = false,
    bnLayout: Boolean = false,
) {
    val partyLabel = if (isCurrent) null else LocalStreamHighlight.current(stream)
    val cardShape = RoundedCornerShape(12.dp)
    val badgeImages = stream.badges.filter { it.imageURL.isNotBlank() }
    val hasBadges = badgeImages.isNotEmpty() || (showFileSizeBadges && stream.behaviorHints.videoSize != null)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .shadow(
                elevation = 2.dp,
                shape = cardShape,
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.04f),
            )
            .clip(cardShape)
            .ambientLiquidGlass(
                shape = cardShape,
                tint = if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.White.copy(alpha = 0.05f)
                }.compositeOver(Color.Black.copy(alpha = StreamGlassTintAlpha)),
                fallback = if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.White.copy(alpha = 0.05f)
                },
                blurRadius = 18.dp,
            )
            .then(
                if (isCurrent || outlined) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.52f),
                        shape = cardShape,
                    )
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Como a TV: fonte de debrid sem plano fica apagada (texto a 45%) com o selo embaixo.
        val contentAlpha = if (stream.debridNoPlan) 0.45f else 1f
        Column(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.alpha(contentAlpha)) {
                // Better Nuvio: os selos da linha nova (imagens do addon ou os de qualidade)
                // respeitam o "em cima / embaixo" dos ajustes de selos.
                val bnQualityTags = if (bnLayout && badgeImages.isEmpty()) stream.bnQualityTags() else emptyList()
                val bnHasTags = badgeImages.isNotEmpty() || bnQualityTags.isNotEmpty()
                if (bnLayout && bnHasTags && badgePlacement == StreamBadgePlacement.TOP) {
                    BnStreamTagRow(badgeImages = badgeImages, stream = stream, qualityTags = bnQualityTags)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (!bnLayout && hasBadges && badgePlacement == StreamBadgePlacement.TOP) {
                    StreamCardBadgeRow(
                        badgeImages = badgeImages,
                        stream = stream,
                        showFileSizeBadges = showFileSizeBadges,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                StreamNameWithInstantService(
                    stream = stream,
                    appendInstantServiceToDefaultName = appendInstantServiceToDefaultName,
                    label = if (bnLayout) stream.bnNameLine() else stream.streamLabel,
                ) {
                    if (isCurrent && !currentLabel.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CurrentStreamBadge(label = currentLabel)
                    }
                    if (partyLabel != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = partyLabel,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Better Nuvio: nome e resolução, tamanho e velocidade, depois os selos (ordem da TV).
                val sizeAndBitrate = if (bnLayout) stream.bnSizeAndBitrate(includeSize = showFileSizeBadges) else null
                if (sizeAndBitrate != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sizeAndBitrate,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            shadow = streamGlassTextShadow(),
                        ),
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val subtitle = stream.streamSubtitle
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            shadow = streamGlassTextShadow(),
                        ),
                        color = Color.White,
                    )
                }

                if (bnLayout) {
                    if (bnHasTags && badgePlacement == StreamBadgePlacement.BOTTOM) {
                        Spacer(modifier = Modifier.height(8.dp))
                        BnStreamTagRow(badgeImages = badgeImages, stream = stream, qualityTags = bnQualityTags)
                    }
                } else if (hasBadges && badgePlacement == StreamBadgePlacement.BOTTOM) {
                    Spacer(modifier = Modifier.height(5.dp))
                    StreamCardBadgeRow(
                        badgeImages = badgeImages,
                        stream = stream,
                        showFileSizeBadges = showFileSizeBadges,
                    )
                }
            }
            if (stream.debridNoPlan) {
                Spacer(modifier = Modifier.height(8.dp))
                DebridNoPlanBadge()
            }
        }

        if (showAddonLogo) {
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.alpha(contentAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!stream.addonLogo.isNullOrBlank()) {
                    AsyncImage(
                        model = stream.addonLogo,
                        contentDescription = stream.addonName,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stream.addonName,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, shadow = streamGlassTextShadow()),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Pílula cheia "Debrid sem assinatura", como o selo da TV (streams.c). */
@Composable
private fun DebridNoPlanBadge() {
    Text(
        text = stringResource(Res.string.bn_debrid_inactive_badge),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFE5484D))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
        color = Color.White,
        maxLines = 1,
    )
}

@Composable
internal fun StreamCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.55f), height = 14.dp, cornerRadius = 4.dp)
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.85f), height = 10.dp, cornerRadius = 4.dp)
    }
}

@Composable
private fun StreamCardBadgeRow(
    badgeImages: List<StreamBadge>,
    stream: StreamItem,
    showFileSizeBadges: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        badgeImages.forEach { badge ->
            StreamBadgeImage(badge = badge)
        }
        if (showFileSizeBadges) {
            StreamFileSizeBadge(stream = stream)
        }
    }
}

@Composable
private fun StreamNameWithInstantService(
    stream: StreamItem,
    appendInstantServiceToDefaultName: Boolean,
    label: String = stream.streamLabel,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val nameStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
        shadow = streamGlassTextShadow(),
    )
    val instantLabel = if (appendInstantServiceToDefaultName) {
        stream.instantServiceLabel()
    } else {
        null
    }
    val showInstantLabel = instantLabel != null
    val visibleState = remember(stream.streamLabel) {
        MutableTransitionState(showInstantLabel)
    }
    visibleState.targetState = showInstantLabel

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f, fill = false),
            style = nameStyle,
            color = Color.White,
        )
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(durationMillis = 260)) +
                expandHorizontally(
                    animationSpec = tween(durationMillis = 260),
                    expandFrom = Alignment.Start,
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
                shrinkHorizontally(
                    animationSpec = tween(durationMillis = 120),
                    shrinkTowards = Alignment.Start,
                ),
            label = "streamNameInstantService",
        ) {
            Text(
                text = " ${instantLabel.orEmpty()}",
                style = nameStyle,
                color = Color.White,
            )
        }
        trailingContent()
    }
}

@Composable
private fun CurrentStreamBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal const val StreamGlassTintAlpha = 0.55f

private val StreamGlassTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.6f),
    offset = Offset(0f, 1f),
    blurRadius = 4f,
)

@Composable
internal fun streamGlassTextShadow(): Shadow? =
    if (isAmbientLiquidGlassActive()) StreamGlassTextShadow else null

private fun StreamItem.instantServiceLabel(): String? {
    val status = debridCacheStatus ?: return null
    if (status.state != StreamDebridCacheState.CACHED) return null
    val providerLabel = DebridProviders.shortName(status.providerId)
        .ifBlank { status.providerName.trim() }
        .ifBlank { DebridProviders.displayName(status.providerId) }
    return "- $providerLabel Instant"
}

/** Better Nuvio: selos de imagem do addon, ou na falta deles os selos de qualidade da TV. */
@Composable
private fun BnStreamTagRow(
    badgeImages: List<StreamBadge>,
    stream: StreamItem,
    qualityTags: List<String>,
) {
    if (badgeImages.isNotEmpty()) {
        // O tamanho já vai na linha de tamanho e velocidade.
        StreamCardBadgeRow(badgeImages = badgeImages, stream = stream, showFileSizeBadges = false)
    } else if (qualityTags.isNotEmpty()) {
        QualityTagRow(
            tags = qualityTags,
            background = Color.White.copy(alpha = 0.1f),
            contentColor = Color.White.copy(alpha = 0.9f),
        )
    }
}
