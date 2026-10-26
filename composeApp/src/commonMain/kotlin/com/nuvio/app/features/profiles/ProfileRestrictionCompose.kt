package com.nuvio.app.features.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeUiState
import com.nuvio.app.features.home.MetaPreview

/** Better Nuvio: a restrição do perfil ativo, para as telas reagirem quando ela muda. */
@Composable
fun rememberActiveProfileRestriction(): ProfileRestriction {
    val restriction by remember {
        ProfileRestrictionRepository.ensureLoaded()
        ProfileRestrictionRepository.active
    }.collectAsStateWithLifecycle()
    return restriction
}

/** Better Nuvio: lista sem o que o perfil não pode ver (restricao_bloqueia), pedindo as classificações que faltam. */
@Composable
fun rememberRestrictedItems(items: List<MetaPreview>): List<MetaPreview> {
    val restriction = rememberActiveProfileRestriction()
    val ratings by ProfileRestrictionRepository.ratings.collectAsStateWithLifecycle()
    LaunchedEffect(items, restriction) {
        ProfileRestrictionRepository.request(items)
    }
    return remember(items, restriction, ratings) {
        ProfileRestrictionRepository.filter(items, restriction, ratings)
    }
}

/** Better Nuvio: fileiras de busca/catálogo filtradas; fileira que fica vazia some. */
@Composable
fun rememberRestrictedSections(sections: List<HomeCatalogSection>): List<HomeCatalogSection> {
    val restriction = rememberActiveProfileRestriction()
    val ratings by ProfileRestrictionRepository.ratings.collectAsStateWithLifecycle()
    LaunchedEffect(sections, restriction) {
        ProfileRestrictionRepository.request(sections.flatMap { it.items })
    }
    return remember(sections, restriction, ratings) {
        if (!restriction.isActive) {
            sections
        } else {
            sections.mapNotNull { section ->
                val allowed = ProfileRestrictionRepository.filter(section.items, restriction, ratings)
                if (allowed.isEmpty()) null else section.copy(items = allowed, availableItemCount = allowed.size)
            }
        }
    }
}

/** Better Nuvio: a Home inteira (destaque e fileiras) filtrada pela restrição. */
@Composable
fun rememberRestrictedHome(state: HomeUiState): HomeUiState {
    val restriction = rememberActiveProfileRestriction()
    val ratings by ProfileRestrictionRepository.ratings.collectAsStateWithLifecycle()
    LaunchedEffect(state.heroItems, state.sections, restriction) {
        ProfileRestrictionRepository.request(state.heroItems + state.sections.flatMap { it.items })
    }
    return remember(state, restriction, ratings) {
        ProfileRestrictionRepository.filterHome(state, restriction, ratings)
    }
}
