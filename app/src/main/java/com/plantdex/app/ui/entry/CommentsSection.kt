package com.plantdex.app.ui.entry

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.plantdex.app.data.repository.Comment
import com.plantdex.app.data.repository.ReportTarget
import com.plantdex.app.data.repository.SocialRepository
import com.plantdex.app.ui.components.ReportDialog
import com.plantdex.app.ui.components.UserAvatar
import com.plantdex.app.ui.components.appContainer
import com.plantdex.app.util.Formatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** 기록 아래의 댓글 목록과 입력창. 차단한 사용자의 댓글은 보이지 않습니다. */
@Composable
fun CommentsSection(
    entryId: String,
    entryOwnerId: String,
    onAuthorClick: (uid: String, name: String) -> Unit,
) {
    val context = LocalContext.current
    val container = appContainer()
    val social = container.socialRepository
    val me by container.authRepository.currentUser.collectAsStateWithLifecycle()
    val blockedIds by container.moderationRepository.blockedIds.collectAsStateWithLifecycle()
    val commentsFlow = remember(entryId) { social.observeComments(entryId).catch { emit(emptyList()) } }
    val comments by commentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val visible = comments.filter { it.authorId !in blockedIds }
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Comment?>(null) }
    var reporting by remember { mutableStateOf<Comment?>(null) }
    val deletedMessage = stringResource(R.string.entry_comments_deleted)
    val reportedMessage = stringResource(R.string.entry_comments_reported)

    fun run(success: String?, action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                success?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: context.getString(R.string.entry_comments_action_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.entry_comments_title, visible.size), style = MaterialTheme.typography.titleMedium)
        visible.forEach { comment ->
            val myUid = me?.uid
            CommentRow(
                comment = comment,
                canDelete = comment.authorId == myUid || entryOwnerId == myUid,
                canReport = comment.authorId != myUid,
                onAuthorClick = { onAuthorClick(comment.authorId, comment.authorName) },
                onDelete = { deleting = comment },
                onReport = { reporting = comment },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(SocialRepository.MAX_COMMENT_LENGTH) },
                placeholder = { Text(stringResource(R.string.entry_comments_placeholder)) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            IconButton(
                enabled = text.isNotBlank() && !sending,
                onClick = {
                    sending = true
                    val toSend = text
                    scope.launch {
                        try {
                            social.addComment(entryId, toSend)
                            text = ""
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.entry_comments_send_failed, e.message), Toast.LENGTH_SHORT).show()
                        } finally {
                            sending = false
                        }
                    }
                },
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.entry_comments_send)) }
        }
    }

    deleting?.let { comment ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.entry_comments_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    run(deletedMessage) { social.deleteComment(comment) }
                }) { Text(stringResource(R.string.entry_comments_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.entry_comments_cancel)) } },
        )
    }
    reporting?.let { comment ->
        ReportDialog(
            title = stringResource(R.string.entry_comments_report_title),
            onDismiss = { reporting = null },
            onReport = { reason ->
                reporting = null
                run(reportedMessage) {
                    container.moderationRepository.report(
                        ReportTarget.Comment,
                        targetId = comment.id,
                        reason = reason,
                        entryId = comment.entryId,
                        targetOwnerId = comment.authorId,
                    )
                }
            },
        )
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    canDelete: Boolean,
    canReport: Boolean,
    onAuthorClick: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top) {
        UserAvatar(comment.authorName, null, Modifier.clickable(onClick = onAuthorClick), size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorName,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(onClick = onAuthorClick),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    Formatters.dateTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
        }
        if (canDelete || canReport) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.entry_comments_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (canDelete) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.entry_comments_delete)) }, onClick = { menu = false; onDelete() })
                    }
                    if (canReport) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.entry_comments_report)) }, onClick = { menu = false; onReport() })
                    }
                }
            }
        }
    }
}
