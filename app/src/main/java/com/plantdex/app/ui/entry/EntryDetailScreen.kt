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
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.plantdex.app.util.Formatters

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
    val name = localizedName(entry)
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
                        IconButton(onClick = { shareEntry(context, entry, name) }) {
                            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.entry_detail_share))
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
                        isOwner = isOwner,
                        onPublicChange = { viewModel.setPublic(data, it) },
                        onOpenMap = { openMap(context, data, name) },
                        onUserClick = { onUserClick(data.ownerId, data.ownerName) },
                        onSpeciesClick = { onSpeciesClick(data.scientificName, name, data.family) },
                        onAuthorClick = onUserClick,
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
            onDismiss = { reporting = false },
            onReport = { reason ->
                reporting = false
                moderate(reportDoneMessage) {
                    moderation.report(ReportTarget.Entry, entry.id, reason, entryId = entry.id, targetOwnerId = entry.ownerId)
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
    isOwner: Boolean,
    onPublicChange: (Boolean) -> Unit,
    onOpenMap: () -> Unit,
    onUserClick: () -> Unit,
    onSpeciesClick: () -> Unit,
    onAuthorClick: (uid: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                ScientificName(entry.scientificName, style = MaterialTheme.typography.titleMedium, maxLines = 2)
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
            if (placeName != null) InfoPill(placeName)
            if (familyText != null) InfoPill(familyText)
            if (genusText != null) InfoPill(genusText)
        }
        OutlinedButton(onClick = onSpeciesClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.entry_detail_view_species))
        }

        // 관찰 정보 카드
        PaperCard(Modifier.fillMaxWidth()) {
            InfoRow(Icons.Outlined.Schedule, stringResource(R.string.entry_detail_captured_at), Formatters.dateTime(entry.capturedAt))
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val location = entry.location
            if (location != null) {
                InfoRow(
                    Icons.Outlined.Place,
                    stringResource(R.string.entry_detail_location),
                    listOfNotNull(
                        location.placeName,
                        Formatters.coordinates(location.latitude, location.longitude),
                    ).joinToString("\n"),
                    onClick = onOpenMap,
                )
            } else {
                InfoRow(Icons.Outlined.Place, stringResource(R.string.entry_detail_location), stringResource(R.string.entry_detail_no_location))
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(Icons.Outlined.Verified, stringResource(R.string.entry_detail_ai_confidence), "${Formatters.percent(entry.score)} (Pl@ntNet)")
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

        // 메모
        if (entry.memo.isNotBlank()) {
            PaperCard(Modifier.fillMaxWidth()) {
                Text(entry.memo, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
        }

        // 공개 설정 (내 기록일 때만)
        if (isOwner) {
            PaperCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.entry_detail_public_toggle), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = entry.isPublic, onCheckedChange = onPublicChange, enabled = !entry.isHidden)
                    }
                    if (entry.isHidden) {
                        Text(
                            stringResource(R.string.entry_detail_hidden_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }

        CommentsSection(entryId = entry.id, entryOwnerId = entry.ownerId, onAuthorClick = onAuthorClick)
        Spacer(Modifier.height(8.dp))
    }
}

/** 관찰 정보 한 줄: 연초록 원 안의 아이콘 + 이름표 + 값. */
@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun shareEntry(context: Context, entry: CollectionEntry, name: String) {
    val text = buildString {
        appendLine("🌿 $name (${entry.scientificName})")
        append(Formatters.dateTime(entry.capturedAt))
        entry.location?.placeName?.let { append(" · $it") }
        appendLine()
        if (entry.memo.isNotBlank()) appendLine(entry.memo)
        appendLine(entry.photoUrl)
        append("#PlantDex")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
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
