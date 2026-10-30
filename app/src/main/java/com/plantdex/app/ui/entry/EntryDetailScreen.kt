package com.plantdex.app.ui.entry

import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.Box
import android.widget.Toast
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.draw.clip
import com.plantdex.app.ui.components.EnglishName
import com.plantdex.app.ui.components.plantName
import com.plantdex.app.ui.components.InfoPill
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.ScientificName
import com.plantdex.app.ui.components.UserAvatar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.plantdex.app.R
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.ui.components.LoadState
import com.plantdex.app.ui.components.LoadingBox
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.data.repository.ReportTarget
import com.plantdex.app.ui.components.PlantBadge
import com.plantdex.app.ui.components.ReportDialog
import com.plantdex.app.ui.components.ReactionBar
import com.plantdex.app.ui.components.appContainer
import com.plantdex.app.ui.components.localizedName
import com.plantdex.app.ui.components.rememberPlantArt
import androidx.compose.material3.CircularProgressIndicator
import com.plantdex.app.util.Formatters
import com.plantdex.app.util.ShareCard
import com.plantdex.app.util.ShareCardText
import com.plantdex.app.data.names.PlantDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    onUserClick: (userId: String, userName: String) -> Unit,
    onSpeciesClick: (scientificName: String, name: String, family: String?) -> Unit,
) {
    val context = LocalContext.current
    val container = appContainer()
    val viewModel: EntryDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                EntryDetailViewModel(entryId, container.collectionRepository, container.authRepository)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var correcting by remember { mutableStateOf(false) }
    var savingCorrection by remember { mutableStateOf(false) }
    val moderation = container.moderationRepository
    val scope = rememberCoroutineScope()

    fun moderate(success: String, action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                Toast.makeText(context, success, Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.entry_detail_action_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(deleted) { if (deleted) onBack() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val entry = (state as? LoadState.Success)?.data
    val plant = entry?.let { plantName(it) }
    var sharing by remember { mutableStateOf(false) }
    val name = plant?.korean.orEmpty()
    val isOwner = entry != null && viewModel.isOwner(entry)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.entry_detail_back))
                    }
                },
                actions = {
                    if (entry != null) {
                        val shareFailed = stringResource(R.string.entry_detail_share_card_failed)
                        IconButton(
                            enabled = !sharing,
                            onClick = {
                                sharing = true
                                scope.launch {
                                    try {
                                        shareEntry(context, container.shareCard, entry, plant)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Toast.makeText(context, shareFailed, Toast.LENGTH_SHORT).show()
                                        shareEntry(context, null, entry, plant)
                                    } finally {
                                        sharing = false
                                    }
                                }
                            },
                        ) {
                            if (sharing) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.entry_detail_share))
                            }
                        }
                        if (isOwner) {
                            IconButton(onClick = { confirmDelete = true }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.entry_detail_delete))
                            }
                        } else {
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.entry_detail_more))
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.entry_detail_report_menu)) },
                                        leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                                        onClick = { menuOpen = false; reporting = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.entry_detail_block_menu, entry.ownerName)) },
                                        leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                                        onClick = { menuOpen = false; confirmBlock = true },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val s = state) {
            LoadState.Loading -> LoadingBox(modifier)
            is LoadState.Error -> MessageBox(stringResource(R.string.entry_detail_load_failed), modifier, body = s.message)
            is LoadState.Success -> {
                val data = s.data
                if (data == null) {
                    MessageBox(stringResource(R.string.entry_detail_not_found), modifier)
                } else {
                    EntryDetailContent(
                        entry = data,
                        name = name,
                        englishName = plant?.english,
                        isOwner = isOwner,
                        onPublicChange = { viewModel.setPublic(data, it) },
                        onCorrectClick = { correcting = true },
                        onOpenMap = { openMap(context, data, name) },
                        onUserClick = { onUserClick(data.ownerId, data.ownerName) },
                        onSpeciesClick = { onSpeciesClick(data.scientificName, name, data.family) },
                        onAuthorClick = onUserClick,
                        onMemoChange = { viewModel.setMemo(data, it) },
                        modifier = modifier,
                    )
                }
            }
        }
    }

    if (reporting && entry != null) {
        val reportDoneMessage = stringResource(R.string.entry_detail_report_done)
        ReportDialog(
            title = stringResource(R.string.entry_detail_report_title),
            target = ReportTarget.Entry,
            onDismiss = { reporting = false },
            onReport = { reason, detail ->
                reporting = false
                moderate(reportDoneMessage) {
                    moderation.report(
                        ReportTarget.Entry,
                        entry.id,
                        reason,
                        detail = detail,
                        entryId = entry.id,
                        targetOwnerId = entry.ownerId,
                    )
                }
            },
        )
    }

    if (correcting && entry != null) {
        val correctedMessage = stringResource(R.string.entry_correct_done)
        CorrectEntryDialog(
            entry = entry,
            saving = savingCorrection,
            onDismiss = { correcting = false },
            onSubmit = { scientificName, commonName ->
                savingCorrection = true
                scope.launch {
                    try {
                        container.collectionRepository.correctEntry(entry.id, scientificName, commonName, family = null)
                        correcting = false
                        Toast.makeText(context, correctedMessage, Toast.LENGTH_LONG).show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                    } finally {
                        savingCorrection = false
                    }
                }
            },
        )
    }

    if (confirmBlock && entry != null) {
        val blockedMessage = stringResource(R.string.entry_detail_blocked_toast)
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text(stringResource(R.string.entry_detail_block_title, entry.ownerName)) },
            text = { Text(stringResource(R.string.entry_detail_block_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    moderate(blockedMessage) { moderation.block(entry.ownerId, entry.ownerName) }
                    onBack()
                }) { Text(stringResource(R.string.entry_detail_block_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text(stringResource(R.string.entry_detail_cancel)) } },
        )
    }

    if (confirmDelete && entry != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.entry_detail_delete_title)) },
            text = { Text(stringResource(R.string.entry_detail_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(entry)
                }) { Text(stringResource(R.string.entry_detail_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.entry_detail_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryDetailContent(
    entry: CollectionEntry,
    name: String,
    englishName: String?,
    isOwner: Boolean,
    onPublicChange: (Boolean) -> Unit,
    onCorrectClick: () -> Unit,
    onOpenMap: () -> Unit,
    onUserClick: () -> Unit,
    onSpeciesClick: () -> Unit,
    onAuthorClick: (uid: String, name: String) -> Unit,
    onMemoChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingMemo by remember { mutableStateOf(false) }
    if (editingMemo) {
        MemoDialog(initial = entry.memo, onDismiss = { editingMemo = false }, onSave = { editingMemo = false; onMemoChange(it) })
    }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 표본처럼 둥글게 들어앉힌 사진
        AsyncImage(
            model = entry.photoUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        ReactionBar(entry, Modifier.fillMaxWidth())

        // 이름·학명·분류 꼬리표
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlantBadge(rememberPlantArt(entry.scientificName, entry.family), size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.headlineSmall)
                EnglishName(englishName, style = MaterialTheme.typography.bodyMedium)
                ScientificName(entry.scientificName, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
        }
        val family = entry.family
        val genus = entry.genus
        val familyText = if (family != null) stringResource(R.string.entry_detail_family, family) else null
        val genusText = if (genus != null) stringResource(R.string.entry_detail_genus, genus) else null
        val placeName = entry.location?.placeName?.takeIf { it.isNotBlank() }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InfoPill(
                Formatters.date(entry.capturedAt),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            // 장소를 누르면 지도 앱에서 촬영 위치를 엽니다.
            if (placeName != null) {
                InfoPill(placeName, Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onOpenMap))
            }
            if (familyText != null) InfoPill(familyText)
            if (genusText != null) InfoPill(genusText)
        }
        OutlinedButton(onClick = onSpeciesClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.entry_detail_view_species))
        }

        // 기록한 사람
        PaperCard(Modifier.fillMaxWidth(), onClick = onUserClick) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(entry.ownerName, null, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.entry_detail_recorded_by),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(entry.ownerName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 메모: 작성자는 500자까지 쓰고 고칠 수 있고, 다른 사람에게는 내용이 있을 때만 보입니다.
        if (isOwner || entry.memo.isNotBlank()) {
            PaperCard(Modifier.fillMaxWidth(), onClick = if (isOwner) ({ editingMemo = true }) else null) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.entry_detail_memo_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (entry.memo.isNotBlank()) {
                            Text(entry.memo, style = MaterialTheme.typography.bodyLarge)
                        } else {
                            Text(
                                stringResource(R.string.entry_detail_memo_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isOwner) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.entry_detail_memo_edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }

        // 공개 설정 (내 기록일 때만)
        if (isOwner) {
            PaperCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.entry_detail_public_toggle), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = entry.isPublic,
                            onCheckedChange = onPublicChange,
                            enabled = !entry.isHidden && !entry.needsCorrection,
                        )
                    }
                    if (entry.isHidden) {
                        Text(
                            stringResource(R.string.entry_detail_hidden_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (entry.needsCorrection) {
                        Text(
                            stringResource(R.string.entry_detail_needs_correction_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        FilledTonalButton(
                            onClick = onCorrectClick,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) { Text(stringResource(R.string.entry_detail_correct_button)) }
                    }
                }
            }
        }

        CommentsSection(entryId = entry.id, entryOwnerId = entry.ownerId, onAuthorClick = onAuthorClick)
        Spacer(Modifier.height(8.dp))
    }
}

/** 메모 쓰기·고치기 창 (최대 [MAX_MEMO_LENGTH]자) */
@Composable
private fun MemoDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.entry_detail_memo_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_MEMO_LENGTH) },
                placeholder = { Text(stringResource(R.string.entry_detail_memo_hint)) },
                minLines = 4,
                maxLines = 8,
                supportingText = { Text(stringResource(R.string.entry_detail_memo_counter, text.length, MAX_MEMO_LENGTH)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text(stringResource(R.string.entry_detail_memo_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.entry_detail_cancel)) } },
    )
}

/** 메모 최대 글자 수 (서버 규칙과 같음) */
const val MAX_MEMO_LENGTH = 500

/**
 * 기록을 공유합니다. [shareCard] 가 있으면 사진·이름·학명·날짜가 들어간 도감 카드 이미지를 만들어 함께 보내고,
 * 없거나(카드 만들기 실패) 사진을 못 받으면 글만 보냅니다.
 */
private suspend fun shareEntry(context: Context, shareCard: ShareCard?, entry: CollectionEntry, plant: PlantDisplayName?) {
    val text = ShareCardText.of(
        korean = plant?.korean ?: entry.displayName,
        english = plant?.english,
        scientificName = entry.scientificName,
        date = Formatters.date(entry.capturedAt),
        placeName = entry.location?.placeName,
        memo = entry.memo,
    )
    val image = shareCard?.render(entry.photoUrl, text)
    val intent = Intent(Intent.ACTION_SEND).apply {
        if (image != null) {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, image)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_TEXT, text.caption(photoUrl = null))
        } else {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text.caption(entry.photoUrl))
        }
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.entry_detail_share_chooser_title)))
}

private fun openMap(context: Context, entry: CollectionEntry, name: String) {
    val location = entry.location ?: return
    val coords = "${location.latitude},${location.longitude}"
    val uri = Uri.parse("geo:$coords?q=$coords(${Uri.encode(name)})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // 지도 앱이 없는 기기
    }
}
