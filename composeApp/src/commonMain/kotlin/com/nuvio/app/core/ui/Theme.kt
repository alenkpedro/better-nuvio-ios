package com.nuvio.app.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nuvio.app.features.settings.ThemeSettingsRepository
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.bn_inter_display_bold
import nuvio.composeapp.generated.resources.bn_inter_display_medium
import nuvio.composeapp.generated.resources.bn_roboto_condensed_bold
import nuvio.composeapp.generated.resources.bn_roboto_condensed_medium
import nuvio.composeapp.generated.resources.bn_roboto_condensed_regular
import nuvio.composeapp.generated.resources.bn_roboto_condensed_semibold
import org.jetbrains.compose.resources.Font

val LocalAppTheme = staticCompositionLocalOf { AppTheme.WHITE }
val LocalThemePalette = staticCompositionLocalOf { ThemeColors.White }

val MaterialTheme.themePalette: ThemeColorPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalThemePalette.current

val MaterialTheme.appTheme: AppTheme
    @Composable
    @ReadOnlyComposable
    get() = LocalAppTheme.current

private fun contentColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF111111) else Color(0xFFF5F7F8)

private fun buildColorScheme(palette: ThemeColorPalette, amoled: Boolean = false) = darkColorScheme(
    primary = palette.secondary,
    onPrimary = palette.onSecondary,
    primaryContainer = palette.focusBackground,
    onPrimaryContainer = contentColorFor(palette.focusBackground),
    secondary = palette.secondaryVariant,
    onSecondary = palette.onSecondaryVariant,
    background = if (amoled) Color.Black else palette.background,
    onBackground = Color(0xFFF5F7F8),
    surface = palette.backgroundElevated,
    onSurface = Color(0xFFF5F7F8),
    surfaceVariant = palette.backgroundCard,
    onSurfaceVariant = Color(0xFF969CA3),
    outline = Color(0xFF252A2A),
    error = Color(0xFFE36A8A),
    onError = Color(0xFFFCE5EC),
)

// Better Nuvio: Inter Display nos títulos de tela e de seção (700, tracking −2 a −3,5%),
// como na TV. Texto corrido, botões e números ficam na fonte do sistema (SF Pro no iOS).
val BetterNuvioTitleFont: FontFamily
    @Composable
    get() = FontFamily(
        Font(Res.font.bn_inter_display_bold, FontWeight.Bold, FontStyle.Normal),
        Font(Res.font.bn_inter_display_medium, FontWeight.Medium, FontStyle.Normal),
    )

// Fonte editorial da TV (text.h, TXT_COND_*): Roboto Condensed em todo o texto das telas
// de conteúdo (Home, detalhes, coleções, Explorar, Biblioteca e Agenda). Menus e Ajustes
// seguem na fonte da interface. Licença OFL em licenses/OFL-Roboto-Condensed.txt.
val BetterNuvioContentFont: FontFamily
    @Composable
    get() = FontFamily(
        Font(Res.font.bn_roboto_condensed_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.bn_roboto_condensed_medium, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.bn_roboto_condensed_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.bn_roboto_condensed_bold, FontWeight.Bold, FontStyle.Normal),
    )

/**
 * Troca a fonte de tudo dentro de [content] pela Roboto Condensed, mantendo tamanhos,
 * pesos e espaçamentos. Usar na raiz das telas de conteúdo, como a TV faz.
 */
@Composable
fun ConteudoEditorial(content: @Composable () -> Unit) {
    val font = BetterNuvioContentFont
    val t = MaterialTheme.typography
    val s = LocalNuvioTypeScale.current
    fun TextStyle.cond() = copy(fontFamily = font)
    val typography = remember(t, font) {
        t.copy(
            displayLarge = t.displayLarge.cond(), displayMedium = t.displayMedium.cond(),
            displaySmall = t.displaySmall.cond(), headlineLarge = t.headlineLarge.cond(),
            headlineMedium = t.headlineMedium.cond(), headlineSmall = t.headlineSmall.cond(),
            titleLarge = t.titleLarge.cond(), titleMedium = t.titleMedium.cond(),
            titleSmall = t.titleSmall.cond(), bodyLarge = t.bodyLarge.cond(),
            bodyMedium = t.bodyMedium.cond(), bodySmall = t.bodySmall.cond(),
            labelLarge = t.labelLarge.cond(), labelMedium = t.labelMedium.cond(),
            labelSmall = t.labelSmall.cond(),
        )
    }
    val scale = remember(s, font) {
        s.copy(
            labelXs = s.labelXs.cond(), labelSm = s.labelSm.cond(), bodySm = s.bodySm.cond(),
            bodyMd = s.bodyMd.cond(), bodyLg = s.bodyLg.cond(), titleSm = s.titleSm.cond(),
            titleMd = s.titleMd.cond(), titleLg = s.titleLg.cond(),
            displaySm = s.displaySm.cond(), displayMd = s.displayMd.cond(),
        )
    }
    CompositionLocalProvider(LocalNuvioTypeScale provides scale) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = typography,
            content = content,
        )
    }
}

private val BodyFont: FontFamily = FontFamily.Default

private val TitleTrackingTight = (-0.035).em
private val TitleTracking = (-0.025).em
private val SectionTracking = (-0.02).em

private val NuvioTypography: Typography
    @Composable
    get() = Typography(
        displayLarge = TextStyle(
            fontFamily = BetterNuvioTitleFont,
            fontSize = NuvioTokens.Type.pageDisplay,
            lineHeight = NuvioTokens.LineHeight.pageDisplay,
            fontWeight = FontWeight.Bold,
            letterSpacing = TitleTrackingTight,
        ),
        headlineLarge = TextStyle(
            fontFamily = BetterNuvioTitleFont,
            fontSize = NuvioTokens.Type.headline,
            lineHeight = NuvioTokens.LineHeight.headline,
            fontWeight = FontWeight.Bold,
            letterSpacing = TitleTracking,
        ),
        titleLarge = TextStyle(
            fontFamily = BetterNuvioTitleFont,
            fontSize = NuvioTokens.Type.titleSm,
            lineHeight = NuvioTokens.LineHeight.materialTitleLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = SectionTracking,
        ),
        titleMedium = TextStyle(
            fontFamily = BodyFont,
            fontSize = NuvioTokens.Type.bodyLg,
            lineHeight = NuvioTokens.LineHeight.bodyMd,
            fontWeight = FontWeight.SemiBold,
        ),
        bodyLarge = TextStyle(
            fontFamily = BodyFont,
            fontSize = NuvioTokens.Type.bodyApp,
            lineHeight = NuvioTokens.LineHeight.bodyApp,
            fontWeight = FontWeight.Normal,
        ),
        bodyMedium = TextStyle(
            fontFamily = BodyFont,
            fontSize = NuvioTokens.Type.bodyMd,
            lineHeight = NuvioTokens.LineHeight.bodyMd,
            fontWeight = FontWeight.Normal,
        ),
        labelLarge = TextStyle(
            fontFamily = BodyFont,
            fontSize = NuvioTokens.Type.bodyMd,
            lineHeight = NuvioTokens.LineHeight.bodySm,
            fontWeight = FontWeight.SemiBold,
        ),
        labelMedium = TextStyle(
            fontFamily = BodyFont,
            fontSize = NuvioTokens.Type.labelSm,
            lineHeight = NuvioTokens.LineHeight.labelXs,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = NuvioTokens.LetterSpacing.label,
        ),
    )

private val NuvioTypeTokens: NuvioTypeScale
    @Composable
    get() = NuvioTypeScale(
        labelXs = TextStyle(
            fontFamily = BodyFont,
            fontSize = NuvioTokens.Type.labelXs,
            lineHeight = NuvioTokens.LineHeight.labelXs,
            fontWeight = FontWeight.SemiBold,
        ),
        labelSm = TextStyle(
            fontFamily = BodyFont,
            fontSize = NuvioTokens.Type.labelSm,
            lineHeight = NuvioTokens.LineHeight.labelSm,
            fontWeight = FontWeight.SemiBold,
        ),
        bodySm = TextStyle(
            fontFamily = BodyFont,
            fontSize = NuvioTokens.Type.bodySm,
            lineHeight = NuvioTokens.LineHeight.bodySm,
            fontWeight = FontWeight.Normal,
        ),
        bodyMd = TextStyle(
            fontFamily = BodyFont,
            fontSize = NuvioTokens.Type.bodyMd,
            lineHeight = NuvioTokens.LineHeight.bodyMd,
            fontWeight = FontWeight.Normal,
        ),
        bodyLg = TextStyle(
            fontFamily = BodyFont,
            fontSize = NuvioTokens.Type.bodyLg,
            lineHeight = NuvioTokens.LineHeight.bodyLg,
            fontWeight = FontWeight.Normal,
        ),
        titleSm = TextStyle(
            fontFamily = BodyFont,
            fontSize = NuvioTokens.Type.titleSm,
            lineHeight = NuvioTokens.LineHeight.titleSm,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMd = TextStyle(
            fontFamily = BetterNuvioTitleFont,
            fontSize = NuvioTokens.Type.titleMd,
            lineHeight = NuvioTokens.LineHeight.titleMd,
            fontWeight = FontWeight.Bold,
            letterSpacing = SectionTracking,
        ),
        titleLg = TextStyle(
            fontFamily = BetterNuvioTitleFont,
            fontSize = NuvioTokens.Type.titleLg,
            lineHeight = NuvioTokens.LineHeight.titleLg,
            fontWeight = FontWeight.Bold,
            letterSpacing = TitleTracking,
        ),
        displaySm = TextStyle(
            fontFamily = BetterNuvioTitleFont,
            fontSize = NuvioTokens.Type.displaySm,
            lineHeight = NuvioTokens.LineHeight.displaySm,
            fontWeight = FontWeight.Bold,
            letterSpacing = TitleTracking,
        ),
        displayMd = TextStyle(
            fontFamily = BetterNuvioTitleFont,
            fontSize = NuvioTokens.Type.displayMd,
            lineHeight = NuvioTokens.LineHeight.displayMd,
            fontWeight = FontWeight.Bold,
            letterSpacing = TitleTrackingTight,
        ),
    )

private val NuvioRippleConfiguration = RippleConfiguration(
    color = Color.Black,
)

@Composable
fun NuvioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appTheme: AppTheme = AppTheme.WHITE,
    amoled: Boolean = false,
    customThemeColors: CustomThemeColors = CustomThemeColors.Default,
    content: @Composable () -> Unit,
) {
    val basePalette = remember(appTheme, customThemeColors) {
        ThemeColors.getColorPalette(appTheme, customThemeColors)
    }
    val palette = basePalette.withBetterNuvioAccent()
    val colorScheme = buildColorScheme(palette, amoled = amoled)
    val tokens = defaultNuvioThemeTokens(palette, amoled = amoled, colorScheme = colorScheme)

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = 1f,
        ),
        LocalNuvioThemeTokens provides tokens,
        LocalNuvioTypeScale provides NuvioTypeTokens,
        LocalRippleConfiguration provides NuvioRippleConfiguration,
        LocalAppTheme provides appTheme,
        LocalThemePalette provides palette,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NuvioTypography,
        ) {
            SkeletonAnimationProvider(content = content)
        }
    }
}

/**
 * Better Nuvio: com a Imersiva ligada, a cor de destaque do app é a cor viva do último
 * título (corviva.h na TV), também na Biblioteca, na Agenda e nos Ajustes (chaves, anel do
 * avatar, seletores, aba ativa). Antes do primeiro título, branco com texto escuro
 * (ajustes_cor_destaque). Com uma cor fixa escolhida, fica a paleta do tema.
 */
@Composable
private fun ThemeColorPalette.withBetterNuvioAccent(): ThemeColorPalette {
    val immersive by ThemeSettingsRepository.dynamicArtworkBackgroundEnabled.collectAsState()
    val last by ThemeSettingsRepository.lastCorViva.collectAsState()
    val target = when {
        !immersive -> secondary
        last != null -> Color(last!!)
        else -> Color.White
    }
    val accent by animateColorAsState(target, animationSpec = tween(450), label = "bnAccent")
    if (!immersive) return this
    val onAccent = accent.onCorViva()
    return copy(
        secondary = accent,
        secondaryVariant = accent.mixedWith(Color.Black, 0.35f),
        accentGradient = listOf(accent),
        nativeAccentHex = formatHexColor(accent.toArgb() and 0xFFFFFF),
        onSecondary = onAccent,
        focusRing = accent,
        focusBackground = background.mixedWith(accent, 0.28f),
    )
}

private fun Color.mixedWith(other: Color, fraction: Float): Color = Color(
    red = red + (other.red - red) * fraction,
    green = green + (other.green - green) * fraction,
    blue = blue + (other.blue - blue) * fraction,
    alpha = 1f,
)
