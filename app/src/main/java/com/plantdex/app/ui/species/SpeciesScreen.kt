package com.plantdex.app.ui.species

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantdex.app.R
import com.plantdex.app.data.catalog.SpeciesKey
import com.plantdex.app.data.info.WikiSummary
import com.plantdex.app.ui.components.EnglishName
import com.plantdex.app.ui.components.plantName
import com.plantdex.app.ui.components.EntryGrid
import com.plantdex.app.ui.components.InfoPill
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.PlantArtImage
import com.plantdex.app.ui.components.ScientificName
import com.plantdex.app.ui.components.SectionHeader
import com.plantdex.app.ui.components.TaxonomySection
import com.plantdex.app.ui.components.appContainer
import com.plantdex.app.ui.components.rememberPlantArt
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** 계절 도감 id → 관찰 시기 이름 */
private val SEASONS = linkedMapOf(
    "spring" to R.string.species_season_spring,
    "summer" to R.string.species_season_summer,
    "autumn" to R.string.species_season_autumn,
    "winter" to R.string.species_season_winter,
)

/**
 * 식물 정보: 그림, 이름·학명, 들어 있는 도감과 관찰 시기, 위키백과 설명, 내 기록.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeciesScreen(
    uid: String,
    scientificName: String,
    name: String,
    family: String?,
    onEntryClick: (entryId: String) -> Unit,
    onCaptureClick: () -> Unit,
    onBack: () -> Unit,
) {
    val container = appContainer()
    val catalog = remember { runCatching { container.catalogRepository.catalog }.getOrNull() }
    val species = remember(scientificName) { catalog?.match(scientificName) }
    val catalogs = remember(species) { species?.let { catalog?.catalogsOf(it.id) }.orEmpty() }
    val seasonResIds = catalogs.mapNotNull { SEASONS[it.id] }
    val seasons = seasonResIds.map { stringResource(it) }
    val plant = plantName(scientificName, species?.name ?: name)
    val title = plant.korean
    val key = remember(scientificName) { SpeciesKey.of(scientificName, catalog) }
    val art = rememberPlantArt(scientificName, family)

    val myEntriesFlow = remember(uid, key) {
        container.collectionRepository.observeMyEntries(uid)
            .map { list -> list.filter { SpeciesKey.of(it.scientificName, catalog) == key } }
            .catch { emit(emptyList()) }
    }
    val myEntries by myEntriesFlow.collectAsStateWithLifecycle(initialValue = null)

    val language = LocalConfiguration.current.locales[0].language
    var wiki by remember { mutableStateOf<WikiSummary?>(null) }
    var wikiLoading by remember { mutableStateOf(true) }
    LaunchedEffect(scientificName, language) {
        wikiLoading = true
        wiki = container.wikipediaClient.summary(listOfNotNull(species?.name, name), scientificName, language)
        wikiLoading = false
    }
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.species_back)) }
                },
            )
        },
    ) { padding ->
        EntryGrid(
            entries = myEntries.orEmpty(),
            onEntryClick = { onEntryClick(it.id) },
            modifier = Modifier.fillMaxSize().padding(padding),
            header = {
                Column(
                    Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 표본 그림을 둥근 액자처럼 크게
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.6f)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlantArtImage(art, Modifier.fillMaxHeight(0.8f).aspectRatio(1f))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, style = MaterialTheme.typography.headlineMedium)
                        EnglishName(plant.english, style = MaterialTheme.typography.bodyMedium)
                        ScientificName(scientificName, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                    val familyText = family?.takeIf { it.isNotBlank() }
                    if (familyText != null || seasons.isNotEmpty() || catalogs.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            familyText?.let { InfoPill(stringResource(R.string.species_family, it)) }
                            if (seasons.isNotEmpty()) {
                                InfoPill(
                                    stringResource(R.string.species_seasons, seasons.joinToString("·")),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            catalogs.forEach { c ->
                                InfoPill(
                                    "${c.emoji} ${c.title}",
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    // 꽃말: 테라코타 색 인용 상자
                    species?.flowerLanguage?.takeIf { it.isNotBlank() }?.let {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ) {
                            Row(Modifier.height(IntrinsicSize.Min).padding(14.dp)) {
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.species_flower_language, it),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }
                    species?.hint?.takeIf { it.isNotBlank() }?.let {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Row(Modifier.padding(14.dp)) {
                                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.species_hint, it), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(stringResource(R.string.species_intro))
                        PaperCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val summary = wiki
                                when {
                                    wikiLoading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                    summary == null -> Text(
                                        stringResource(R.string.species_wiki_not_found),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    else -> {
                                        Text(summary.extract, style = MaterialTheme.typography.bodyMedium)
                                        summary.pageUrl?.let { url ->
                                            TextButton(onClick = { uriHandler.openUri(url) }) {
                                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Text(
                                                    if (summary.language != language) {
                                                        stringResource(R.string.species_wiki_more_with_language, summary.language)
                                                    } else {
                                                        stringResource(R.string.species_wiki_more)
                                                    },
                                                )
                                            }
                                        }
                                        Text(
                                            stringResource(R.string.species_wiki_source),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // 분류 체계 그래프 (계 → 종)
                    TaxonomySection(
                        scientificName = scientificName,
                        species = plant,
                        family = family,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val mine = myEntries
                    when {
                        mine == null -> Unit
                        mine.isEmpty() -> PaperCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(stringResource(R.string.species_not_recorded), style = MaterialTheme.typography.titleMedium)
                                Button(onClick = onCaptureClick) {
                                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                                    Text(stringResource(R.string.species_capture))
                                }
                            }
                        }
                        else -> SectionHeader(stringResource(R.string.species_my_records_count, mine.size))
                    }
                }
            },
        )
    }
}
