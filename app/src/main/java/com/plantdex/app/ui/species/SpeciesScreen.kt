package com.plantdex.app.ui.species

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantdex.app.data.catalog.SpeciesKey
import com.plantdex.app.data.info.WikiSummary
import com.plantdex.app.ui.components.EntryGrid
import com.plantdex.app.ui.components.PlantArtImage
import com.plantdex.app.ui.components.appContainer
import com.plantdex.app.ui.components.rememberPlantArt
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** 계절 도감 id → 관찰 시기 이름 */
private val SEASONS = linkedMapOf("spring" to "봄", "summer" to "여름", "autumn" to "가을", "winter" to "겨울")

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
    val seasons = catalogs.mapNotNull { SEASONS[it.id] }
    val title = species?.name ?: name
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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
            )
        },
    ) { padding ->
        EntryGrid(
            entries = myEntries.orEmpty(),
            onEntryClick = { onEntryClick(it.id) },
            modifier = Modifier.fillMaxSize().padding(padding),
            header = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlantArtImage(art, Modifier.size(96.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.headlineSmall)
                            Text(scientificName, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                            family?.takeIf { it.isNotBlank() }?.let {
                                Text("$it (과)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (seasons.isNotEmpty()) {
                        Text("관찰하기 좋은 때: ${seasons.joinToString("·")}", style = MaterialTheme.typography.bodyMedium)
                    }
                    species?.hint?.takeIf { it.isNotBlank() }?.let {
                        Text("찾는 법: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (catalogs.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            catalogs.forEach { c -> AssistChip(onClick = {}, label = { Text("${c.emoji} ${c.title}") }) }
                        }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("소개", style = MaterialTheme.typography.titleMedium)
                            val summary = wiki
                            when {
                                wikiLoading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                summary == null -> Text(
                                    "설명을 찾지 못했어요.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                else -> {
                                    Text(summary.extract, style = MaterialTheme.typography.bodyMedium)
                                    summary.pageUrl?.let { url ->
                                        TextButton(onClick = { uriHandler.openUri(url) }) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Text(" 위키백과에서 더 보기" + if (summary.language != language) " (${summary.language})" else "")
                                        }
                                    }
                                    Text(
                                        "출처: 위키백과 (CC BY-SA)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    val mine = myEntries
                    when {
                        mine == null -> Unit
                        mine.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("아직 기록하지 않은 식물이에요", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = onCaptureClick) {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                                Text("  찾아서 촬영하기")
                            }
                        }
                        else -> Text("내 기록 ${mine.size}건", style = MaterialTheme.typography.titleMedium)
                    }
                }
            },
        )
    }
}
