package com.plantdex.app.ui.species

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.plantdex.app.R
import com.plantdex.app.data.info.SpeciesFacts
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.data.taxonomy.TaxonKoreanNames
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.PlantBadge
import com.plantdex.app.ui.components.ScientificName
import com.plantdex.app.ui.components.SectionHeader
import com.plantdex.app.ui.components.plantName
import com.plantdex.app.ui.components.rememberPlantArt
import com.plantdex.app.util.Formatters
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId

/** 같은 과의 다른 식물: 그림 + 이름을 가로로 넘겨 보고, 누르면 그 식물 정보로 갑니다. */
@Composable
fun SameFamilySection(
    family: String,
    entries: List<CollectionEntry>,
    onSpeciesClick: (scientificName: String, name: String, family: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    val familyName = TaxonKoreanNames.known(family) ?: family
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(R.string.species_same_family, familyName))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(entries, key = { it.id }) { entry ->
                val name = plantName(entry)
                PaperCard(onClick = { onSpeciesClick(entry.scientificName, name.korean, entry.family) }) {
                    Column(
                        Modifier.width(112.dp).padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PlantBadge(rememberPlantArt(entry.scientificName, entry.family), size = 44.dp)
                        Text(name.korean, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ScientificName(entry.scientificName, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/** 이 식물을 기록한 다른 사람들의 사진 (최근 순, 가로 넘김). 누르면 그 기록으로 갑니다. */
@Composable
fun CommunityPhotosSection(
    entries: List<CollectionEntry>?,
    plantName: String,
    onEntryClick: (entryId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(R.string.species_community_photos))
        when {
            entries == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            entries.isEmpty() -> Text(
                stringResource(R.string.species_community_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Column(
                        Modifier
                            .width(120.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(role = Role.Button) { onEntryClick(entry.id) },
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AsyncImage(
                            model = entry.listImageUrl,
                            contentDescription = stringResource(R.string.species_community_photo, entry.ownerName, plantName),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                        Text(entry.ownerName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            Formatters.date(entry.capturedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** '알고 있나요?' 카드: GBIF 숫자와 PlantDex 공개 기록으로 만든 짧은 사실들. */
@Composable
fun FactsSection(
    facts: SpeciesFacts?,
    factsLoading: Boolean,
    community: List<CollectionEntry>?,
    modifier: Modifier = Modifier,
) {
    val lines = buildList {
        val number = NumberFormat.getIntegerInstance()
        facts?.worldOccurrences?.let { add(stringResource(R.string.species_fact_world, number.format(it))) }
        facts?.koreaOccurrences?.takeIf { it > 0 }?.let { add(stringResource(R.string.species_fact_korea, number.format(it))) }
        facts?.topMonths?.takeIf { it.isNotEmpty() }?.let { months ->
            add(stringResource(R.string.species_fact_months, months.joinToString("·") { stringResource(R.string.species_fact_month, it) }))
        }
        if (!community.isNullOrEmpty()) {
            val people = community.map { it.ownerId }.toSet().size
            val first = community.minOf { it.capturedAt }
            add(stringResource(R.string.species_fact_community, people, Formatters.date(first)))
            val zone = ZoneId.systemDefault()
            val months = community.groupingBy { Instant.ofEpochMilli(it.capturedAt).atZone(zone).monthValue }.eachCount()
                .entries.sortedByDescending { it.value }.take(2).map { it.key }
            if (community.size >= 3) add(stringResource(R.string.species_fact_community_months, months.joinToString("·") { stringResource(R.string.species_fact_month, it) }))
        }
        facts?.foreignNames?.takeIf { it.isNotEmpty() }?.let { names ->
            add(stringResource(R.string.species_fact_names, names.joinToString(" · ") { "${it.languageLabel} ${it.name}" }))
        }
    }
    if (lines.isEmpty() && !factsLoading) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(R.string.species_facts)) {
            if (factsLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        PaperCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (lines.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.species_facts_source),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
