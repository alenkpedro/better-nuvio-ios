package com.nuvio.app.features.artwork

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.tmdb.TmdbArtworkOption
import com.nuvio.app.features.tmdb.TmdbArtworkOptions
import com.nuvio.app.features.tmdb.TmdbMetadataService
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.bn_artwork_automatic
import nuvio.composeapp.generated.resources.bn_artwork_backgrounds
import nuvio.composeapp.generated.resources.bn_artwork_empty
import nuvio.composeapp.generated.resources.bn_artwork_logos
import nuvio.composeapp.generated.resources.bn_artwork_title
import org.jetbrains.compose.resources.stringResource

/**
 * Better Nuvio: a tela "Trocar arte" (trocaarte.c) como folha: abas Fundos e Logos,
 * "Automático" primeiro e as imagens do TMDB depois. A escolha vale na hora e fica
 * guardada por título e por perfil (ArtworkChoiceRepository).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkPickerSheet(
    id: String,
    type: String,
    title: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val choices by remember {
        ArtworkChoiceRepository.ensureLoaded()
        ArtworkChoiceRepository.uiState
    }.collectAsStateWithLifecycle()
    val choice = choices[ArtworkChoiceRepository.key(id)]
    var options by remember(id) { mutableStateOf<TmdbArtworkOptions?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(id, type) {
        loading = true
        options = runCatching { TmdbMetadataService.fetchArtworkOptions(id, type) }.getOrNull()
        loading = false
    }

    NuvioModalBottomSheet(
        onDismissRequest = { scope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) } },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = nuvioSafeBottomPadding(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.bn_artwork_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.nuvio.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ArtworkTabs(selected = tab, onSelected = { tab = it })

            val isLogo = tab == 1
            val list = if (isLogo) options?.logos.orEmpty() else options?.backdrops.orEmpty()
            val selectedUrl = if (isLogo) choice?.logo else choice?.background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 460.dp),
            ) {
                when {
                    loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                        strokeWidth = 2.dp,
                    )
                    else -> ArtworkGrid(
                        items = list,
                        columns = if (isLogo) 3 else 2,
                        isLogo = isLogo,
                        selectedUrl = selectedUrl,
                        onSelect = { url ->
                            if (isLogo) ArtworkChoiceRepository.setLogo(id, url)
                            else ArtworkChoiceRepository.setBackground(id, url)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkTabs(selected: Int, onSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(Res.string.bn_artwork_backgrounds, Res.string.bn_artwork_logos).forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) Color.White else MaterialTheme.nuvio.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ArtworkGrid(
    items: List<TmdbArtworkOption>,
    columns: Int,
    isLogo: Boolean,
    selectedUrl: String?,
    onSelect: (String?) -> Unit,
) {
    // "Automático" é sempre a primeira célula; null = a regra de sempre.
    val cells: List<TmdbArtworkOption?> = listOf<TmdbArtworkOption?>(null) + items
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        cells.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { option ->
                    val isSelected = if (option == null) selectedUrl.isNullOrBlank() else option.url == selectedUrl
                    ArtworkCell(
                        option = option,
                        isLogo = isLogo,
                        selected = isSelected,
                        onClick = { onSelect(option?.url) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (items.isEmpty()) {
            Text(
                text = stringResource(Res.string.bn_artwork_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.nuvio.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun ArtworkCell(
    option: TmdbArtworkOption?,
    isLogo: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(if (isLogo) Color(0xFF2A2A2E) else Color.White.copy(alpha = 0.06f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.1f),
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (option == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(Res.string.bn_artwork_automatic),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        } else {
            AsyncImage(
                model = option.thumbnail,
                contentDescription = null,
                contentScale = if (isLogo) ContentScale.Fit else ContentScale.Crop,
                modifier = if (isLogo) Modifier.fillMaxSize().padding(10.dp) else Modifier.fillMaxSize(),
            )
            option.language?.takeIf { isLogo }?.let { language ->
                Text(
                    text = language.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
    }
}
