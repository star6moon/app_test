package com.plantdex.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.plantdex.app.R
import com.plantdex.app.data.filter.PeriodFilter
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.data.model.UserProfile
import com.plantdex.app.data.names.PlantDisplayName
import com.plantdex.app.data.premium.Premium
import com.plantdex.app.data.premium.PremiumFeature
import com.plantdex.app.data.search.EntrySearch
import com.plantdex.app.data.search.NameHit
import com.plantdex.app.data.search.SearchFilters
import com.plantdex.app.data.search.VisibilityFilter
import com.plantdex.app.data.taxonomy.TaxonKoreanNames
import com.plantdex.app.ui.collection.EntryListViewModel
import com.plantdex.app.ui.components.EntryGridItem
import com.plantdex.app.ui.components.LoadState
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.PlantBadge
import com.plantdex.app.ui.components.PremiumLockedBox
import com.plantdex.app.ui.components.ScientificName
import com.plantdex.app.ui.components.SectionHeader
import com.plantdex.app.ui.components.appContainer
import com.plantdex.app.ui.components.rememberPlantArt
import com.plantdex.app.ui.components.softFilterChipBorder
import com.plantdex.app.ui.components.softFilterChipColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 검색: 글자를 치면 ① 앱이 아는 식물(이름 목록·도감) ② 내 기록 을 함께 보여줍니다.
 * 필터(도감·과·기간·공개 여부·위치)로 내 기록을 좁힙니다. 식물을 누르면 식물 정보로, 기록을 누르면 기록으로 갑니다.
 * (검색과 필터는 추후 유료 기능 후보라 [Premium] 으로 잠글 수 있게 해 두었습니다)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    user: UserProfile,
    onEntryClick: (entryId: String) -> Unit,
    onSpeciesClick: (scientificName: String, name: String, family: String?) -> Unit,
    onBack: () -> Unit,
) {
    val container = appContainer()
    var query by rememberSaveable { mutableStateOf("") }
    var filters by remember { mutableStateOf(SearchFilters()) }
    val filtersUnlocked = Premium.isUnlocked(PremiumFeature.Filters)

    val viewModel: EntryListViewModel = viewModel(
        factory = viewModelFactory { initializer { EntryListViewModel(container.collectionRepository.observeMyEntries(user.uid)) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val catalog = remember { runCatching { container.catalogRepository.catalog }.getOrNull() }
    val entries = (state as? LoadState.Success)?.data.orEmpty()

    fun nameOf(entry: CollectionEntry): PlantDisplayName = container.plantNames.resolve(
        entry.scientificName,
        entry.commonName?.takeIf { entry.commonNameLanguage == null || entry.commonNameLanguage == "ko" },
        entry.commonName?.takeIf { entry.commonNameLanguage == "en" },
    )

    val trimmed = query.trim()
    val matchedEntries by produceState(emptyList<CollectionEntry>(), entries, trimmed, filters) {
        value = withContext(Dispatchers.Default) { EntrySearch.search(entries, trimmed, filters, catalog, ::nameOf) }
    }
    val plantHits by produceState(emptyList<NameHit>(), trimmed) {
        value = if (trimmed.length < 2) emptyList() else withContext(Dispatchers.Default) {
            val q = EntrySearch.normalize(trimmed)
            val fromCatalog = catalog?.species.orEmpty()
                .filter { it.name.lowercase().contains(q) || it.scientificName.lowercase().contains(q) }
                .map { NameHit(it.scientificName, it.name, container.plantNameBook.english(it.scientificName)) }
            (fromCatalog + container.plantNameBook.search(trimmed)).distinctBy { it.scientificName.lowercase() }.take(MAX_PLANT_HITS)
        }
    }
    val families = remember(entries) { EntrySearch.families(entries) }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.search_back)) }
                },
            )
        },
    ) { padding ->
        if (!Premium.isUnlocked(PremiumFeature.Search)) {
            PremiumLockedBox(stringResource(R.string.search_title), Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(60) },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.search_clear)) }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )
            if (filtersUnlocked) {
                FilterRow(
                    filters = filters,
                    catalogs = catalog?.catalogs.orEmpty().map { it.id to "${it.emoji} ${it.title}" },
                    families = families,
                    onChange = { filters = it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            val showPlants = plantHits.isNotEmpty()
            val showEntries = trimmed.isNotEmpty() || !filters.isEmpty
            when {
                state is LoadState.Error -> MessageBox(stringResource(R.string.collection_load_failed), Modifier.fillMaxSize(), body = (state as LoadState.Error).message, emoji = "⚠️")
                !showPlants && !showEntries -> MessageBox(
                    stringResource(R.string.search_start_title),
                    Modifier.fillMaxSize(),
                    body = stringResource(R.string.search_start_body),
                    emoji = "🔍",
                )
                !showPlants && matchedEntries.isEmpty() -> MessageBox(
                    stringResource(R.string.search_empty_title),
                    Modifier.fillMaxSize(),
                    body = stringResource(R.string.search_empty_body),
                    emoji = "🌫️",
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showPlants) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(stringResource(R.string.search_plants_count, plantHits.size), Modifier.padding(horizontal = 4.dp))
                        }
                        items(plantHits, key = { "plant:" + it.scientificName }, span = { GridItemSpan(maxLineSpan) }) { hit ->
                            PlantHitRow(hit, onClick = { onSpeciesClick(hit.scientificName, hit.korean ?: hit.scientificName, null) })
                        }
                    }
                    if (showEntries) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(stringResource(R.string.search_records_count, matchedEntries.size), Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                        }
                        if (matchedEntries.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    stringResource(R.string.search_records_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                        items(matchedEntries, key = { it.id }) { entry -> EntryGridItem(entry, onClick = { onEntryClick(entry.id) }) }
                    }
                }
            }
        }
    }
}

private const val MAX_PLANT_HITS = 12

/** 식물 한 줄: 그림 + 한국어 이름 + 영어 이름 + 학명 */
@Composable
private fun PlantHitRow(hit: NameHit, onClick: () -> Unit) {
    PaperCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlantBadge(rememberPlantArt(hit.scientificName, null), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(hit.korean ?: hit.scientificName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                hit.english?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ScientificName(hit.scientificName, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** 필터 칩 줄: 도감 ▾ / 과 ▾ / 기간 ▾ / 공개 ▾ / 위치 있음 / 초기화 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    filters: SearchFilters,
    catalogs: List<Pair<String, String>>,
    families: List<String>,
    onChange: (SearchFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        MenuChip(
            label = catalogs.firstOrNull { it.first == filters.catalogId }?.second ?: stringResource(R.string.search_filter_catalog),
            selected = filters.catalogId != null,
            options = listOf(null to stringResource(R.string.search_filter_any_catalog)) + catalogs,
            onPick = { onChange(filters.copy(catalogId = it)) },
        )
        if (families.isNotEmpty()) {
            MenuChip(
                label = filters.family?.let { familyLabel(it) } ?: stringResource(R.string.search_filter_family),
                selected = filters.family != null,
                options = listOf(null to stringResource(R.string.search_filter_any_family)) + families.map { it to familyLabel(it) },
                onPick = { onChange(filters.copy(family = it)) },
            )
        }
        val periods = listOf(PeriodFilter.All, PeriodFilter.Today, PeriodFilter.LastMonth, PeriodFilter.Last3Months, PeriodFilter.YearAgoMonth)
        MenuChip(
            label = if (filters.period == PeriodFilter.All) stringResource(R.string.search_filter_period) else filters.period.label,
            selected = filters.period != PeriodFilter.All,
            options = periods.map { it to it.label },
            onPick = { onChange(filters.copy(period = it ?: PeriodFilter.All)) },
        )
        val visibilities = listOf(
            VisibilityFilter.All to stringResource(R.string.search_filter_visibility_all),
            VisibilityFilter.PublicOnly to stringResource(R.string.search_filter_public),
            VisibilityFilter.PrivateOnly to stringResource(R.string.search_filter_private),
        )
        MenuChip(
            label = if (filters.visibility == VisibilityFilter.All) stringResource(R.string.search_filter_visibility) else visibilities.first { it.first == filters.visibility }.second,
            selected = filters.visibility != VisibilityFilter.All,
            options = visibilities,
            onPick = { onChange(filters.copy(visibility = it ?: VisibilityFilter.All)) },
        )
        FilterChip(
            selected = filters.withLocationOnly,
            onClick = { onChange(filters.copy(withLocationOnly = !filters.withLocationOnly)) },
            label = { Text(stringResource(R.string.search_filter_with_location)) },
            shape = CircleShape,
            colors = softFilterChipColors(),
            border = softFilterChipBorder(filters.withLocationOnly),
        )
        if (!filters.isEmpty) {
            TextButton(onClick = { onChange(SearchFilters()) }) { Text(stringResource(R.string.search_filter_reset)) }
        }
    }
}

@Composable
private fun familyLabel(family: String): String = TaxonKoreanNames.known(family)?.let { "$it ($family)" } ?: family

/** 누르면 목록이 열리는 칩. [options] 의 첫 항목은 보통 '전체'(null) 입니다. */
@Composable
private fun <T> MenuChip(label: String, selected: Boolean, options: List<Pair<T?, String>>, onPick: (T?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            shape = CircleShape,
            colors = softFilterChipColors(),
            border = softFilterChipBorder(selected),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(value) })
            }
        }
    }
}
