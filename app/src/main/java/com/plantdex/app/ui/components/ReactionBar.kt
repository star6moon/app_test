package com.plantdex.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantdex.app.data.model.CollectionEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val LikeRed = Color(0xFFE53950)

/** 좋아요(♥ 개수)와 책갈피 버튼 */
@Composable
fun ReactionBar(entry: CollectionEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reactions = appContainer().reactionRepository
    val likedIds by reactions.myLikedIds.collectAsStateWithLifecycle()
    val bookmarkIds by reactions.myBookmarkIds.collectAsStateWithLifecycle()
    val liked = entry.id in likedIds
    val bookmarked = bookmarkIds?.contains(entry.id) == true
    val scope = rememberCoroutineScope()
    // 서버 응답을 기다리는 동안 같은 버튼을 다시 누르지 못하게 합니다.
    var likeBusy by remember { mutableStateOf(false) }
    var bookmarkBusy by remember { mutableStateOf(false) }

    fun run(onDone: () -> Unit, failure: String, action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
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
            enabled = !likeBusy,
            onClick = {
                likeBusy = true
                run({ likeBusy = false }, "좋아요를 반영하지 못했어요") { reactions.setLiked(entry.id, !liked) }
            },
        ) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (liked) "좋아요 취소" else "좋아요",
                tint = if (liked) LikeRed else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            entry.likeCount.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            enabled = !bookmarkBusy,
            onClick = {
                bookmarkBusy = true
                run({ bookmarkBusy = false }, "책갈피를 반영하지 못했어요") { reactions.setBookmarked(entry.id, !bookmarked) }
            },
        ) {
            Icon(
                if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (bookmarked) "책갈피 해제" else "책갈피",
                tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
