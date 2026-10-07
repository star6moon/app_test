package com.plantdex.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.ui.collection.EntryListViewModel
import com.plantdex.app.ui.components.LoadState
import com.plantdex.app.ui.components.LoadingBox
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.appContainer
import com.plantdex.app.ui.components.localizedName
import com.plantdex.app.util.Formatters

/** 모든 사용자의 최근 공개 도감 기록. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onEntryClick: (entryId: String) -> Unit,
    onUserClick: (userId: String, userName: String) -> Unit,
) {
    val container = appContainer()
    val viewModel: EntryListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { EntryListViewModel(container.collectionRepository.observeFeed()) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("둘러보기") }) }) { padding ->
        val modifier = Modifier.padding(padding)
        when (val s = state) {
            LoadState.Loading -> LoadingBox(modifier)
            is LoadState.Error -> MessageBox("불러오지 못했어요", modifier, body = s.message)
            is LoadState.Success ->
                if (s.data.isEmpty()) {
                    MessageBox("아직 공유된 기록이 없어요", modifier, body = "첫 번째로 식물을 기록해 공유해 보세요!")
                } else {
                    LazyColumn(
                        modifier = modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(s.data, key = { it.id }) { entry ->
                            FeedCard(
                                entry = entry,
                                onClick = { onEntryClick(entry.id) },
                                onUserClick = { onUserClick(entry.ownerId, entry.ownerName) },
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun FeedCard(entry: CollectionEntry, onClick: () -> Unit, onUserClick: () -> Unit) {
    val name = localizedName(entry)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onUserClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.ownerName.take(1).ifEmpty { "?" },
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(entry.ownerName, style = MaterialTheme.typography.titleSmall)
        }
        AsyncImage(
            model = entry.photoUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
        )
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(entry.scientificName, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
            Text(
                listOfNotNull(Formatters.dateTime(entry.capturedAt), entry.location?.placeName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.memo.isNotBlank()) {
                Spacer(Modifier.size(4.dp))
                Text(entry.memo, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
