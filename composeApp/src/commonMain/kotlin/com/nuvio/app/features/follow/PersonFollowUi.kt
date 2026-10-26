package com.nuvio.app.features.follow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.corViva
import com.nuvio.app.core.ui.onCorViva
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.bn_person_follow
import nuvio.composeapp.generated.resources.bn_person_followed_people
import nuvio.composeapp.generated.resources.bn_person_following
import org.jetbrains.compose.resources.stringResource

@Composable
private fun rememberFollowedPeople(): List<FollowedPerson> {
    val people by remember {
        FollowedPeopleRepository.ensureLoaded()
        FollowedPeopleRepository.uiState
    }.collectAsStateWithLifecycle()
    return people
}

/** Better Nuvio: o "Seguir" da página da pessoa (seguir.c), uma pílula que vira "Seguindo". */
@Composable
fun PersonFollowButton(
    tmdbId: Int,
    name: String,
    photo: String?,
    modifier: Modifier = Modifier,
) {
    if (tmdbId <= 0) return
    val people = rememberFollowedPeople()
    val following = people.any { it.tmdbId == tmdbId }
    val cor = corViva()
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .then(
                if (following) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
                } else {
                    Modifier.background(cor)
                },
            )
            .clickable { FollowedPeopleRepository.toggle(tmdbId, name, photo) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val content = if (following) MaterialTheme.colorScheme.onSurface else cor.onCorViva()
        Icon(
            imageVector = if (following) Icons.Rounded.Check else Icons.Rounded.PersonAdd,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(if (following) Res.string.bn_person_following else Res.string.bn_person_follow),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = content,
            maxLines = 1,
        )
    }
}

/** As outras pessoas que o perfil segue, no pé da página da pessoa. Some quando não há. */
@Composable
fun FollowedPeopleRow(
    currentTmdbId: Int,
    onOpenPerson: (FollowedPerson) -> Unit,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val people = rememberFollowedPeople().filter { it.tmdbId != currentTmdbId }
    if (people.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(Res.string.bn_person_followed_people),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = horizontalPadding),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(people.asReversed(), key = { it.tmdbId }) { person ->
                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenPerson(person) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!person.photo.isNullOrBlank()) {
                            AsyncImage(
                                model = person.photo,
                                contentDescription = person.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(68.dp),
                            )
                        } else {
                            Text(
                                text = person.name.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
