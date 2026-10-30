package com.plantdex.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.plantdex.app.R
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.ui.collection.EntryListViewModel
import com.plantdex.app.ui.components.EnglishName
import com.plantdex.app.ui.components.plantName
import com.plantdex.app.ui.components.LoadState
import com.plantdex.app.ui.components.LoadingBox
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.PillToggle
import com.plantdex.app.ui.components.ReactionBar
import com.plantdex.app.ui.components.ScientificName
import com.plantdex.app.ui.components.UserAvatar
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
    onSearchClick: () -> Unit = {},
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
                    val all = stringResource(R.string.feed_filter_all)
                    val following = stringResource(R.string.feed_filter_following)
                    PillToggle(
                        options = listOf(false, true),
                        selected = followingOnly,
                        label = { if (it) following else all },
                        onSelect = { followingOnly = it },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.feed_search))
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
            is LoadState.Error -> MessageBox(stringResource(R.string.feed_load_failed), modifier, body = s.message, emoji = "⚠️")
            is LoadState.Success -> {
                // 차단한 사용자의 기록은 보이지 않게 합니다.
                val visible = s.data.filter { it.ownerId !in blockedIds }
                if (visible.isEmpty()) {
                    if (followingOnly) {
                        MessageBox(
                            stringResource(R.string.feed_following_empty_title),
                            modifier,
                            body = stringResource(R.string.feed_following_empty_body),
                            emoji = "👥",
                        )
                    } else {
                        MessageBox(
                            stringResource(R.string.feed_empty_title),
                            modifier,
                            body = stringResource(R.string.feed_empty_body),
                            emoji = "🌿",
                        )
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

/** 피드 카드: 머리줄(아바타·이름·날짜) + 표본처럼 끼운 사진 + 반응 + 식물 이름·학명·장소·메모. */
@Composable
private fun FeedCard(entry: CollectionEntry, onClick: () -> Unit, onUserClick: () -> Unit) {
    val plant = plantName(entry)
    val name = plant.korean
    PaperCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onUserClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(entry.ownerName, null, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.ownerName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Formatters.dateTime(entry.capturedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AsyncImage(
            model = entry.photoUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        ReactionBar(entry, Modifier.fillMaxWidth().padding(horizontal = 4.dp))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            EnglishName(plant.english)
            ScientificName(entry.scientificName)
            val placeName = entry.location?.placeName
            if (!placeName.isNullOrBlank()) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Place,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        placeName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (entry.memo.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(entry.memo, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
