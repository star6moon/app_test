package com.plantdex.app.ui.collection

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.plantdex.app.R
import com.plantdex.app.data.catalog.CatalogProgress
import com.plantdex.app.data.catalog.SpeciesProgress
import com.plantdex.app.data.model.UserProfile
import com.plantdex.app.ui.components.LoadState
import com.plantdex.app.ui.components.LoadingBox
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.PlantArtImage
import com.plantdex.app.ui.components.appContainer
import com.plantdex.app.ui.components.softFilterChipBorder
import com.plantdex.app.ui.components.softFilterChipColors

private enum class SpeciesFilter(@StringRes val labelRes: Int) {
    All(R.string.collection_filter_all),
    Collected(R.string.collection_filter_collected),
    Missing(R.string.collection_filter_missing),
}

/** 주제별 도감 한 권: 번호가 매겨진 종 목록과 발견 여부. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogDetailScreen(
    user: UserProfile,
    catalogId: String,
    onSpeciesClick: (scientificName: String, name: String) -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(progress?.let { "${it.catalog.emoji} ${it.catalog.title}" }.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.collection_back))
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val s = state) {
            LoadState.Loading -> LoadingBox(modifier)
            is LoadState.Error -> MessageBox(
                stringResource(R.string.collection_load_failed),
                modifier,
                body = s.message,
                emoji = "⚠️",
            )
            is LoadState.Success ->
                if (progress == null) {
                    MessageBox(stringResource(R.string.collection_catalog_not_found), modifier, emoji = "📖")
                } else {
                    CatalogGrid(
                        progress = progress,
                        onSpeciesClick = { item -> onSpeciesClick(item.species.scientificName, item.species.name) },
                        modifier = modifier,
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 도감 머리말: 소개 + 진행률
                PaperCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            progress.catalog.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.collection_catalog_progress, progress.collected, progress.total),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            if (progress.isComplete) CompleteBadge()
                        }
                        ProgressBar(progress.collected, progress.total)
                    }
                }
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
                            label = { Text(stringResource(R.string.collection_filter_chip, stringResource(option.labelRes), count)) },
                            shape = CircleShape,
                            colors = softFilterChipColors(),
                            border = softFilterChipBorder(filter == option),
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

/**
 * 표본 칸 하나. 발견한 종은 사진을 종이 카드에 끼워 넣고 초록 번호표를 붙이며,
 * 미발견 종은 종 그림을 빛바랜 회색 실루엣처럼 흐리게 보여줍니다.
 */
@Composable
private fun SpeciesCell(number: Int, item: SpeciesProgress, onClick: () -> Unit) {
    val latest = item.latest
    PaperCard(onClick = onClick) {
        Box(
            Modifier
                .padding(start = 6.dp, top = 6.dp, end = 6.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.small)
                .background(
                    if (latest != null) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            if (latest != null) {
                AsyncImage(
                    model = latest.listImageUrl,
                    contentDescription = item.species.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.collection_collected_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                )
            } else if (item.species.art != null) {
                // 미발견: 종 그림을 회색조로 바래고 흐리게 보여줍니다.
                PlantArtImage(
                    art = item.species.art,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(0.72f)
                        .clip(CircleShape)
                        .alpha(0.4f)
                        .desaturate(),
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
                "No. %03d".format(number),
                style = MaterialTheme.typography.labelSmall,
                color = if (latest != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (latest != null) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f)
                        },
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                item.species.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (latest != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                if (latest != null) stringResource(R.string.collection_record_count, item.entries.size) else stringResource(R.string.collection_not_collected),
                style = MaterialTheme.typography.labelSmall,
                color = if (latest != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 그린 내용을 회색조로 바꿉니다 (미발견 종의 빛바랜 그림용). */
private fun Modifier.desaturate(): Modifier = drawWithCache {
    val paint = Paint().apply {
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }
    onDrawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), paint)
            drawContent()
            canvas.restore()
        }
    }
}
