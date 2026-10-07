package com.plantdex.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.util.Formatters

/** 도감 기록을 사진 격자로 보여줍니다. [header] 는 격자 맨 위에 한 줄 전체로 들어갑니다. */
@Composable
fun EntryGrid(
    entries: List<CollectionEntry>,
    onEntryClick: (CollectionEntry) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        items(entries, key = { it.id }) { entry ->
            EntryGridItem(entry, onClick = { onEntryClick(entry) })
        }
    }
}

@Composable
private fun EntryGridItem(entry: CollectionEntry, onClick: () -> Unit) {
    val name = localizedName(entry)
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = entry.photoUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
        )
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.scientificName,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Formatters.date(entry.capturedAt) + if (entry.likeCount > 0) "  ♥ ${entry.likeCount}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 수집 현황 요약 (기록 수 / 종 수). */
@Composable
fun CollectionSummary(entries: List<CollectionEntry>, modifier: Modifier = Modifier) {
    val speciesCount = entries.map { it.scientificName }.distinct().size
    Column(modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(
            "수집한 식물 ${speciesCount}종",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "총 ${entries.size}개의 기록",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
