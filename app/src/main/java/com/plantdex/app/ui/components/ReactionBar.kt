package com.plantdex.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantdex.app.R
import com.plantdex.app.data.model.CollectionEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 좋아요(♥ 개수, 테라코타색)·댓글 수·책갈피 버튼 */
@Composable
fun ReactionBar(entry: CollectionEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reactions = appContainer().reactionRepository
    val likedIds by reactions.myLikedIds.collectAsStateWithLifecycle()
    val bookmarkIds by reactions.myBookmarkIds.collectAsStateWithLifecycle()
    // 내 좋아요·책갈피 목록을 불러오기 전에는 상태를 모르므로 버튼을 잠시 막아 둡니다.
    val likesLoaded = likedIds != null
    val bookmarksLoaded = bookmarkIds != null
    val liked = likedIds?.contains(entry.id) == true
    val bookmarked = bookmarkIds?.contains(entry.id) == true
    val scope = rememberCoroutineScope()
    // 서버 응답을 기다리는 동안 같은 버튼을 다시 누르지 못하게 합니다.
    var likeBusy by remember { mutableStateOf(false) }
    var bookmarkBusy by remember { mutableStateOf(false) }
    val likeFailedMessage = stringResource(R.string.common_reaction_like_failed)
    val bookmarkFailedMessage = stringResource(R.string.common_reaction_bookmark_failed)

    fun run(onDone: () -> Unit, failure: String, action: suspend () -> Unit) {
        scope.launch {
            try {
                // 화면은 바로 바뀌므로 서버 응답을 오래 기다리지 않습니다 (오프라인이면 연결될 때 반영).
                withTimeoutOrNull(3_000) { action() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, failure, Toast.LENGTH_SHORT).show()
            } finally {
                onDone()
            }
        }
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            enabled = likesLoaded && !likeBusy,
            onClick = {
                likeBusy = true
                run({ likeBusy = false }, likeFailedMessage) { reactions.setLiked(entry.id, !liked) }
            },
        ) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(if (liked) R.string.common_reaction_unlike else R.string.common_reaction_like),
                tint = if (liked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            entry.likeCount.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (liked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 댓글 수는 보여주기만 합니다 (댓글은 기록 상세 화면에서).
        Spacer(Modifier.width(16.dp))
        Icon(
            Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            entry.commentCount.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            enabled = bookmarksLoaded && !bookmarkBusy,
            onClick = {
                bookmarkBusy = true
                run({ bookmarkBusy = false }, bookmarkFailedMessage) { reactions.setBookmarked(entry.id, !bookmarked) }
            },
        ) {
            Icon(
                if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = stringResource(if (bookmarked) R.string.common_reaction_remove_bookmark else R.string.common_reaction_bookmark),
                tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
