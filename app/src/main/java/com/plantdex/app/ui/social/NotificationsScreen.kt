package com.plantdex.app.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantdex.app.R
import com.plantdex.app.data.repository.AppNotification
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.UserAvatar
import com.plantdex.app.ui.components.appContainer
import com.plantdex.app.util.Formatters
import kotlinx.coroutines.launch

/** 좋아요·댓글·팔로우 알림 목록. 화면을 열면 모두 읽음으로 표시합니다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onEntryClick: (entryId: String) -> Unit,
    onUserClick: (uid: String, name: String) -> Unit,
    onBack: () -> Unit,
) {
    val container = appContainer()
    val social = container.socialRepository
    val notifications by social.notifications.collectAsStateWithLifecycle()
    val blockedIds by container.moderationRepository.blockedIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val visible = notifications.filter { it.actorUid !in blockedIds }

    LaunchedEffect(notifications.count { !it.read }) {
        runCatching { social.markAllRead() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.social_notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.social_back)) }
                },
            )
        },
    ) { padding ->
        if (visible.isEmpty()) {
            MessageBox(
                stringResource(R.string.social_notifications_empty),
                Modifier.padding(padding),
                body = stringResource(R.string.social_notifications_empty_body),
                emoji = "🔔",
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { n ->
                    NotificationRow(
                        n,
                        onClick = {
                            val entryId = n.entryId
                            if (entryId != null) onEntryClick(entryId) else onUserClick(n.actorUid, n.actorName)
                        },
                        onDelete = { scope.launch { runCatching { social.deleteNotification(n.id) } } },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, onClick: () -> Unit, onDelete: () -> Unit) {
    val icon = when (n.type) {
        "like" -> "❤️"
        "comment" -> "💬"
        "follow" -> "👋"
        else -> "🔔"
    }
    // 알림 한 줄을 종이 카드 한 장에. 읽지 않은 알림은 연한 크라프트색 바탕.
    PaperCard(Modifier.fillMaxWidth(), onClick = onClick) {
        ListItem(
            leadingContent = { UserAvatar(n.actorName, null) },
            headlineContent = { Text("$icon ${n.body}", maxLines = 2, style = MaterialTheme.typography.bodyMedium) },
            supportingContent = {
                if (n.createdAt > 0) {
                    Text(
                        Formatters.dateTime(n.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingContent = {
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.social_notification_delete)) }
            },
            colors = ListItemDefaults.colors(
                containerColor = if (n.read) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer,
            ),
        )
    }
}
