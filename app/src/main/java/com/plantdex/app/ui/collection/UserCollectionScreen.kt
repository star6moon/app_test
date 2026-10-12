package com.plantdex.app.ui.collection

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.plantdex.app.data.repository.ReportTarget
import com.plantdex.app.ui.components.ReportDialog
import com.plantdex.app.ui.profile.ProfileHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.ui.components.CollectionSummary
import com.plantdex.app.ui.components.EntryGrid
import com.plantdex.app.ui.components.LoadState
import com.plantdex.app.ui.components.LoadingBox
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.appContainer

/** 다른 사용자의 프로필과 공개 도감. 팔로우, 차단·신고를 할 수 있습니다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserCollectionScreen(
    userId: String,
    userName: String,
    onEntryClick: (entryId: String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = appContainer()
    val viewModel: EntryListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { EntryListViewModel(container.collectionRepository.observePublicEntriesOf(userId)) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profileFlow = remember(userId) { container.socialRepository.observeProfile(userId).catch { emit(null) } }
    val profile by profileFlow.collectAsStateWithLifecycle(initialValue = null)
    val followingIds by container.socialRepository.followingIds.collectAsStateWithLifecycle()
    val me by container.authRepository.currentUser.collectAsStateWithLifecycle()
    val isMe = me?.uid == userId
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }

    fun run(success: String?, action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                success?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "처리하지 못했어요: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${profile?.displayName ?: userName}님의 도감") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (!isMe) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "더 보기") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("신고하기") }, onClick = { menuOpen = false; reporting = true })
                                DropdownMenuItem(text = { Text("차단") }, onClick = { menuOpen = false; confirmBlock = true })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val header: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                profile?.let { p ->
                    ProfileHeader(
                        profile = p,
                        isMe = isMe,
                        isFollowing = followingIds?.let { userId in it },
                        onFollowChange = { follow ->
                            run(if (follow) "${p.displayName}님을 팔로우해요" else null) {
                                container.socialRepository.setFollowing(userId, follow)
                            }
                        },
                    )
                }
                val entries = (state as? LoadState.Success)?.data
                if (entries != null && entries.isNotEmpty()) CollectionSummary(entries)
                if (entries != null && entries.isEmpty()) {
                    Text(
                        "공개된 기록이 없어요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        when (val s = state) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Error -> MessageBox("불러오지 못했어요", Modifier.padding(padding), body = s.message)
            is LoadState.Success -> EntryGrid(
                entries = s.data,
                onEntryClick = { onEntryClick(it.id) },
                modifier = Modifier.padding(padding),
                header = header,
            )
        }
    }

    if (reporting) {
        ReportDialog(
            title = "사용자 신고",
            onDismiss = { reporting = false },
            onReport = { reason ->
                reporting = false
                run("신고했어요. 검토 후 조치할게요.") {
                    container.moderationRepository.report(ReportTarget.User, targetId = userId, reason = reason, targetOwnerId = userId)
                }
            },
        )
    }
    if (confirmBlock) {
        val name = profile?.displayName ?: userName
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("${name}님을 차단할까요?") },
            text = { Text("차단하면 서로의 기록·댓글이 보이지 않고 알림도 오지 않아요. 설정에서 언제든 풀 수 있어요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    run("차단했어요.") {
                        container.moderationRepository.block(userId, name)
                        runCatching { container.socialRepository.setFollowing(userId, false) }
                    }
                    onBack()
                }) { Text("차단") }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("취소") } },
        )
    }
}

@Composable
internal fun EntryCollectionContent(
    state: LoadState<List<CollectionEntry>>,
    emptyTitle: String,
    onEntryClick: (CollectionEntry) -> Unit,
    modifier: Modifier = Modifier,
    emptyBody: String? = null,
    showSummary: Boolean = true,
) {
    when (state) {
        LoadState.Loading -> LoadingBox(modifier)
        is LoadState.Error -> MessageBox("불러오지 못했어요", modifier, body = state.message)
        is LoadState.Success ->
            if (state.data.isEmpty()) {
                MessageBox(emptyTitle, modifier, body = emptyBody)
            } else {
                EntryGrid(
                    entries = state.data,
                    onEntryClick = onEntryClick,
                    modifier = modifier,
                    header = if (showSummary) summaryHeader(state.data) else null,
                )
            }
    }
}

private fun summaryHeader(entries: List<CollectionEntry>): @Composable () -> Unit = { CollectionSummary(entries) }
