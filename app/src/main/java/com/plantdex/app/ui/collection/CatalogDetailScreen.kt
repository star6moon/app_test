package com.plantdex.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.plantdex.app.data.catalog.CatalogProgress
import com.plantdex.app.data.catalog.SpeciesProgress
import com.plantdex.app.data.model.UserProfile
import com.plantdex.app.ui.components.LoadState
import com.plantdex.app.ui.components.LoadingBox
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.appContainer

private enum class SpeciesFilter(val label: String) { All("전체"), Collected("발견"), Missing("미발견") }

/** 주제별 도감 한 권: 번호가 매겨진 종 목록과 발견 여부. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogDetailScreen(
    user: UserProfile,
    catalogId: String,
    onEntryClick: (entryId: String) -> Unit,
    onCaptureClick: () -> Unit,
    onBack: () -> Unit,
) {
    val container = appContainer()
    val viewModel: CatalogProgressViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CatalogProgressViewModel(user.uid, container.collectionRepository, container.catalogRepository)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress = (state as? LoadState.Success)?.data?.catalogs?.firstOrNull { it.catalog.id == catalogId }
    var hintFor by remember { mutableStateOf<SpeciesProgress?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(progress?.let { "${it.catalog.emoji} ${it.catalog.title}" }.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val s = state) {
            LoadState.Loading -> LoadingBox(modifier)
            is LoadState.Error -> MessageBox("불러오지 못했어요", modifier, body = s.message)
            is LoadState.Success ->
                if (progress == null) {
                    MessageBox("도감을 찾을 수 없어요", modifier)
                } else {
                    CatalogGrid(
                        progress = progress,
                        onSpeciesClick = { item ->
                            val latest = item.latest
                            if (latest != null) onEntryClick(latest.id) else hintFor = item
                        },
                        modifier = modifier,
                    )
                }
        }
    }

    hintFor?.let { item ->
        AlertDialog(
            onDismissRequest = { hintFor = null },
            icon = { Icon(Icons.Filled.LocalFlorist, contentDescription = null) },
            title = { Text(item.species.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.species.scientificName, fontStyle = FontStyle.Italic)
                    Text(item.species.hint)
                    Text(
                        "아직 발견하지 못했어요. 찾아서 촬영하면 도감에 등록돼요!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    hintFor = null
                    onCaptureClick()
                }) { Text("촬영하러 가기") }
            },
            dismissButton = { TextButton(onClick = { hintFor = null }) { Text("닫기") } },
        )
    }
}

@Composable
private fun CatalogGrid(
    progress: CatalogProgress,
    onSpeciesClick: (SpeciesProgress) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(SpeciesFilter.All) }
    // 번호는 필터와 상관없이 도감 순서대로 고정합니다.
    val numbered = progress.items.withIndex().toList()
    val visible = when (filter) {
        SpeciesFilter.All -> numbered
        SpeciesFilter.Collected -> numbered.filter { it.value.collected }
        SpeciesFilter.Missing -> numbered.filter { !it.value.collected }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    progress.catalog.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${progress.collected} / ${progress.total}종 발견",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (progress.isComplete) CompleteBadge()
                }
                ProgressBar(progress.collected, progress.total)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeciesFilter.entries.forEach { option ->
                        val count = when (option) {
                            SpeciesFilter.All -> progress.total
                            SpeciesFilter.Collected -> progress.collected
                            SpeciesFilter.Missing -> progress.total - progress.collected
                        }
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text("${option.label} $count") },
                        )
                    }
                }
            }
        }
        items(visible, key = { it.value.species.id }) { (index, item) ->
            SpeciesCell(number = index + 1, item = item, onClick = { onSpeciesClick(item) })
        }
    }
}

@Composable
private fun SpeciesCell(number: Int, item: SpeciesProgress, onClick: () -> Unit) {
    val latest = item.latest
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = if (latest != null) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            if (latest != null) {
                AsyncImage(
                    model = latest.photoUrl,
                    contentDescription = item.species.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "발견",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                )
            } else {
                Icon(
                    Icons.Filled.LocalFlorist,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.align(Alignment.Center).size(44.dp),
                )
            }
            Text(
                "No.%02d".format(number),
                style = MaterialTheme.typography.labelSmall,
                color = if (latest != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (latest != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surface,
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                item.species.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (latest != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                if (latest != null) "기록 ${item.entries.size}개" else "미발견",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
