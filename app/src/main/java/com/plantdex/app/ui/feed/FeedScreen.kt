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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.plantdex.app.R
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.ui.collection.EntryListViewModel
import com.plantdex.app.ui.components.LoadState
import com.plantdex.app.ui.components.LoadingBox
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.ReactionBar
import com.plantdex.app.ui.components.appContainer
import com.plantdex.app.ui.components.localizedName
import com.plantdex.app.util.Formatters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest

/** 모든 사용자의 최근 공개 도감 기록. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun FeedScreen(
    onEntryClick: (entryId: String) -> Unit,
    onUserClick: (userId: String, userName: String) -> Unit,
    onNotificationsClick: () -> Unit,
) {
    val container = appContainer()
    var followingOnly by rememberSaveable { mutableStateOf(false) }
    val allViewModel: EntryListViewModel = viewModel(
        key = "feed_all",
        factory = viewModelFactory {
            initializer { EntryListViewModel(container.collectionRepository.observeFeed()) }
        },
    )
    val followingViewModel: EntryListViewModel = viewModel(
        key = "feed_following",
        factory = viewModelFactory {
            initializer {
                EntryListViewModel(
                    container.socialRepository.followingIds
                        .filterNotNull()
                        .distinctUntilChanged()
                        .flatMapLatest { container.collectionRepository.observeFeedOf(it) },
                )
            }
        },
    )
    val state by (if (followingOnly) followingViewModel else allViewModel).state.collectAsStateWithLifecycle()
    val blockedIds by container.moderationRepository.blockedIds.collectAsStateWithLifecycle()
    val notifications by container.socialRepository.notifications.collectAsStateWithLifecycle()
    val unread = notifications.count { !it.read && it.actorUid !in blockedIds }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feed_title)) },
                actions = {
                    SingleChoiceSegmentedButtonRow(Modifier.padding(end = 4.dp)) {
                        listOf(false to R.string.feed_filter_all, true to R.string.feed_filter_following).forEachIndexed { i, (value, labelRes) ->
                            SegmentedButton(
                                selected = followingOnly == value,
                                onClick = { followingOnly = value },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                                icon = {},
                            ) { Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    IconButton(onClick = onNotificationsClick) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else "$unread") } }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.feed_notifications))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val s = state) {
            LoadState.Loading -> LoadingBox(modifier)
            is LoadState.Error -> MessageBox(stringResource(R.string.feed_load_failed), modifier, body = s.message)
            is LoadState.Success -> {
                // 차단한 사용자의 기록은 보이지 않게 합니다.
                val visible = s.data.filter { it.ownerId !in blockedIds }
                if (visible.isEmpty()) {
                    if (followingOnly) {
                        MessageBox(
                            stringResource(R.string.feed_following_empty_title),
                            modifier,
                            body = stringResource(R.string.feed_following_empty_body),
                        )
                    } else {
                        MessageBox(stringResource(R.string.feed_empty_title), modifier, body = stringResource(R.string.feed_empty_body))
                    }
                } else {
                    LazyColumn(
                        modifier = modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(visible, key = { it.id }) { entry ->
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
        ReactionBar(entry, Modifier.fillMaxWidth().padding(horizontal = 4.dp))
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
